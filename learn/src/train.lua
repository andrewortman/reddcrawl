local nn = require "nn"
local nngraph = require "nngraph"
local optim = require "optim"
local lfs = require "lfs"
local network = require "network"
local datautil = require "datautil"
local logger = require "logger"
local threads = require "threads"
local _ = require "moses"
local dbg = require "lib/debugger"

require "cutorch"
require "cunn"

--- set up command line arguments
local cmd = torch.CmdLine()
local function parseOptions() 
  cmd:text("reddcrawl-learn training script (requires nvidia gpu)")
  cmd:text("options:")
  cmd:option("--rnnsize", 768, "size of each RNN layer")
  cmd:option("--rnnlayers", 1, "number of RNN layers")
  cmd:option("--dropout", 0.0, "dropout ratio (set to 0 to disable)")
  cmd:option("--seqlength", 128, "sequence length for training")
  cmd:option("--gradclamp", 8, "max abs value of a gradient ( set to 0 to disable )")
  cmd:option("--minibatchsize", 1, "number of stories per training batch")
  cmd:option("--datadir", "./data/train", "batch file directory")
  cmd:option("--metadir", "./data/meta", "metadata file directory")
  cmd:option("--cachedir", "./cache/train", "caching directory for preprocessed batch files")
  cmd:option("--modeldir", "./models", "output models directory")
  cmd:option("--gpus", "", "gpu worker distribution (eg: '1:3;2:4' puts 3 workers on gpu #1 and 4 workers on gpu #2)")
  cmd:option("--initrange", 0.1, "uniform random numbers to set the weights")
  cmd:option("--maxepochs", 0, "max number of epochs to run")
  cmd:option("--gatesquash", 2, "factor to squash the GRU reset/dynamics gate")
  cmd:option("--loadmodel", "", "if specified, a model to start with instead of creating a fresh one")
  cmd:option("--reset", false, "if a model was loaded, set this flag to start the batch number back to zero")
  cmd:option("--graph", false, "whether or not to show the graphs during training")

  local options = cmd:parse(arg);

  local gpumap = options.gpus
  options.gpus = {}
  options.numworkers = 0
  for gpuIdString, gpuWorkerString in string.gmatch(gpumap, '(%d+):(%d+)') do
    local gpuId = tonumber(gpuIdString)
    local gpuWorkers = tonumber(gpuWorkerString)
    if _.contains(options.gpus, {gpuId}) then
      logger:fatal("gpu already exists in mapping! check your --gpus option")
      os.exit(1)
    end

    options.gpus[gpuId] = gpuWorkers
    options.numworkers = options.numworkers + gpuWorkers
  end

  if options.numworkers == 0 then
    logger:fatal("no gpu options set - check --gpus option")
    os.exit(1)
  end

  return options
end

local options = parseOptions();

-- only need to load gnuplot if we have options.gnuplot set
if options.graph then
  require "gnuplot"
end

-- create (or load) the base network
local base = {}
if options.loadmodel ~= "" then
  logger:info("loading model '%s' from disk..", options.loadmodel)
  local loadedModel = network.load(options.loadmodel)
  logger:info("overriding rnn size with %d", loadedModel.options.rnnsize)
  options.rnnsize = loadedModel.options.rnnsize
  logger:info("overriding rnn layers with %d", loadedModel.options.rnnlayers)
  options.rnnlayers = loadedModel.options.rnnlayers
  base.network = loadedModel.network

  base.loadedModel = loadedModel
else
  logger:info("creating a new model from scratch")
  base.network = network.create(options.rnnsize, options.rnnlayers, options.dropout, options.gatesquash)
end

-- criterion (using MSE for now)
base.criterion = nn.MSECriterion(false) --size_average = false (we average across the entire story at the end of each worker thread)

base.params, base.gradParams = base.network:getParameters() --we need to get the flattened parameters BEFORE we clone
if options.loadmodel == "" then
  logger:info("initializing params uniformly between +/-%f", options.initrange)
  base.params:uniform(-options.initrange, options.initrange) -- init the parameters to be uniformly randomly distirbuted
end
logger:info("the network has a total of %d parameters.", base.params:size(1))


-- build out the list of workers - each worker does a single story and can live on any gpu
-- there can be multiple workers on a gpu in order to utilize as much of the compute power as possible
-- worker.gpuid / worker.globalid / worker.localid

-- stuff we initialize in the worker itself:
-- worker.network.base - copy of the base network
-- worker.network.clones - time-seperated clones for the network
-- worker.criterion - copy of the criterion
-- worker.zerostate - copy of the zero state

local workers = {} 
local currentWorkerCount = 0
for gpuid, workerCount in pairs(options.gpus) do 
  for workerLocalId = 1, workerCount do
    currentWorkerCount = currentWorkerCount + 1

    local worker = {}
    worker.gpuid = gpuid
    worker.localid = workerLocalId
    worker.globalid = currentWorkerCount
    table.insert(workers, worker)
  end
end

-- now kick off the initialization of the worker pool
logger:info("starting %d worker threads", options.numworkers)
local workerPool = threads.Threads(
  options.numworkers,
  function(threadid)
    -- these libraries contain the types that we deserialize in the second callback
    -- function - this is an artifact of the threads library
    require "nn"
    require "nngraph"
    require "cunn"
    require "cutorch"
    
    -- thread specific logger
    __logger = require "logger"    
    -- important: this is saved in the worker's thread state (notice: not local) - you wont be able to access the following items outside
    worker = workers[threadid] 
    cutorch.setDevice(worker.gpuid)
  end,
  function(threadid) 
    local network = require "network"

    __logger:info("initializing network on GPU %d for worker %d", workers[threadid].gpuid, workers[threadid].globalid)

    -- during forward prop, we need a table of tensors that represent the RNN state for the first sample 
    -- and during backward prop, we need a table of tensors to represent the gradient of the rnn state for the last step
    -- these can both be satisfied by a list of zeroed tensors
    worker.zerostate = {}
    for i = 1, options.rnnlayers do
      worker.zerostate[i] = torch.CudaTensor(options.rnnsize):zero()
    end

    -- copy over the criterion and base network over
    worker.criterion = base.criterion:cuda()
    worker.network = {}
    worker.network.base = base.network:cuda()
    worker.network.parameters, worker.network.gradParameters = worker.network.base:getParameters()

    -- clone in this thread
    worker.network.clones = network.clone(base.network, options.seqlength)

    __logger:info("worker %d on gpu %d initialized",  worker.globalid, worker.gpuid)
  end)

-- this will perform a single training step on an entire minibatch - returns (loss, gradient)
local function trainMinibatch(minibatch) 
  -- keep track of the minibatch metrics
  local minibatchLoss = 0
  local minibatchSamples = 0
  local minibatchGradParameters = nil

  workerPool:specific(false) --turns on the default queuing behavior

  -- for each story in the minibatch, submit the story to be processed by one of the workers
  for idx, storyHost in pairs(minibatch) do
    workerPool:addjob(
      function()
        --todo: can we make this a thread state variable?
        local _ = require "moses"
        local datautil = require "datautil"

        cutorch.setDevice(worker.gpuid) -- set the device that needs to be used in this thread
        cutorch.reserveStreams(32)
        cutorch.setStream(worker.localid)
        worker.network.base:zeroGradParameters() -- zero out grad params before training

        local story = datautil.copyStoryToGpu(storyHost) -- copy story from host to gpu
        
        --stories have an arbitrary number of history items, so we need to 
        --chunk them up into the smallest number of equal length slices as possible
        --there will be some clipped off at the end, but that is ok
        local numSlices = math.ceil(story.size/options.seqlength)
        local sliceSize = math.floor(story.size/numSlices)

        -- for graphing (host side)
        local graphTimestamp = torch.Tensor(numSlices * sliceSize)
        local graphScore = torch.Tensor(numSlices * sliceSize)
        local graphScorePredicted = torch.Tensor(numSlices * sliceSize)

        -- story loss
        local storyLoss = 0

        -- RNN outputs between time steps
        local netStates = {[0] = worker.zerostate}

        -- Actual outputs between time steps (storage for when we do backprop)
        local netPredictions = {}

        -- device storage for losses (we will do a sum at the end)
        local storyLosses = torch.CudaTensor(numSlices*sliceSize):zero()

        -- for each slice in the stories history
        for sliceNum = 0, numSlices-1 do 
          -- forward prop through each sample, storing the full input and the full output into the inputs/ouputs table
          for sample = 1, sliceSize do 
             -- kick the clone into training mode (enables the optional dropout)
            worker.network.clones[sample]:training()   

            local sampleIdx = sliceNum*sliceSize + sample
            local input = {story.history[sampleIdx]}
            input = _.append(input, story.metadata)
            input = _.append(input, netStates[sample-1])

            local output = worker.network.clones[sample]:forward(input) --the entire net output (the predictions + rnn state output)
            local expected = story.expected[sampleIdx]
            
            if options.graph then
              graphTimestamp[sampleIdx] = story.history[sampleIdx][1]
              -- graphScoreExpected[sampleIdx] = expected[1] 
              graphScore[sampleIdx] = story.history[sampleIdx][2]
              graphScorePredicted[sampleIdx] = output[1][1]
            end

            -- store the output split into the rnn output and the rnn state into two different tables
            netPredictions[sample] = output[1]
            netStates[sample] = {}
            for i = 1, options.rnnlayers do
              netStates[sample][i] = output[i+1] 
            end

            --sum to the mean loss with the criterion's error
            storyLosses[sampleIdx] = worker.criterion:forward(output[1], expected)
          end

          --keeps track of the gradients of the output for backprop - since we start backwards our last gradient is all zero for the RNN states
          local lastRNNStateGradients = worker.zerostate
          for sample = sliceSize, 1, -1 do  
            -- we need to backprop (backwards)
            -- backwards requires the input and the gradient of the output
            -- the input is easy - it is just the sample + the previous' sample rnn state 
            -- the gradient of the output is simply the gradient of the output (calculated by the criterion) + the gradient of the next input (which is the output)
            local sampleIdx = sliceNum*sliceSize + sample

            local input = {story.history[sampleIdx]}
            input = _.append(input, story.metadata)
            input = _.append(input, netStates[sample-1])

            local output = netPredictions[sample]
            local expected = story.expected[sampleIdx]
            --calculate the prediction gradient
            local predictionGradient = worker.criterion:backward(output, expected)

            --then we can backprop, which returns the input gradient with respect to the error
            local inputGradient = worker.network.clones[sample]:backward(input, {predictionGradient, unpack(lastRNNStateGradients)})
            lastRNNStateGradients = {}
            for i = #inputGradient - options.rnnlayers + 1, #inputGradient do
              table.insert(lastRNNStateGradients, inputGradient[i])
            end
          end
          -- move the last rnn state to be the new initial state
          netStates[0] = netStates[#netStates]
        end

        local samplesProcessed = numSlices * sliceSize
        local gradParams = worker.network.gradParameters:clone()
        return storyLosses:sum(), gradParams, samplesProcessed, graphTimestamp, graphScore, graphScorePredicted
      end,
      function(storyLoss, gradParams, samplesProcessed, graphTimestamp, graphScore, graphScorePredicted)
        -- this code is run on the main thread (ie synchronized)

        if minibatchGradParameters == nil then
          minibatchGradParameters = gradParams
        else
          minibatchGradParameters = minibatchGradParameters:add(gradParams)
        end

        minibatchLoss = minibatchLoss + storyLoss
        minibatchSamples = minibatchSamples + samplesProcessed

        if options.graph then
          gnuplot.figure(1)
          gnuplot.plot({"story", graphTimestamp, graphScore:clone():div(datautil.SCORE_SCALE),'-'}, {"predicted", graphTimestamp, graphScorePredicted:clone():div(datautil.SCORE_SCALE),'-'})
          gnuplot.title("output view")
        end
      end)
  end
  workerPool:synchronize()

  -- now we need to compute the final grad parameters and the total loss
  minibatchLoss = minibatchLoss / minibatchSamples
  minibatchGradParameters = minibatchGradParameters:div(minibatchSamples)
  return minibatchLoss, minibatchGradParameters
end

-- get a list of batch files
local batchFiles = datautil.getFileListing(options.datadir)
logger:info("registered " .. #batchFiles .. " batch files")

-- set up the optimizer
local optimState = {}
local optimConfig = {
}

-- this is just a wrapper around a single train/optimize step - it handles the business logic around the parameters
-- before and after a train step
local function optimizeStep(trainStep) 
  local function feval(newParameters)
    -- we have to tell each worker thread to update it's network parameters
    workerPool:specific(true) --turn on specific mode so we can target individual workers
    for idx = 1,options.numworkers do 
      workerPool:addjob(idx, function() 
        worker.network.parameters:copy(newParameters) 
      end)
    end
    workerPool:synchronize()

    -- now call the function that actually does the train step
    local loss, gradient = trainStep()
    logger:info("[TRAIN] minibatch train complete - average loss of %f", loss)

    if options.gradclamp > 0 then
      logger:info("[TRAIN] clamping gradients to +/- %f; current peaks: %f, %f", options.gradclamp, gradient:min(), gradient:max())
      gradient:clamp(-options.gradclamp, options.gradclamp)
    end

    -- optim requires feval to return the loss and the gradient with respect to the loss
    return loss, gradient:double()
  end

  -- use adam (todo: make this configurable?)
  return optim.adam(feval, base.params, optimConfig, optimState)
end

-- an "epoch" is the entire dataset through a single time, we can go indefinitely or through a 
--specified number of epochs
local epochNumber = 1
if options.reset == false and base.loadedModel ~= nil then
  logger:info("skipping to epoch #%d", base.loadedModel.epoch)
  epochNumber = base.loadedModel.epoch
end

while epochNumber <= options.maxepochs or options.maxepochs == 0 do
  logger:info("starting epoch #%d", epochNumber)
  local startBatch = 1
  if options.reset == false and base.loadedModel ~= nil then
    startBatch = base.loadedModel.batch + 1
    logger:info("skipping to batch #%d", startBatch)
  end

  -- iterate through the batch files we have
  for batchIdx = startBatch, #batchFiles do
    local batchFile = batchFiles[batchIdx]
    logger:info("loading batch file '%s'", batchFile)

    local batch = datautil.loadBatch(batchFile, options.cachedir, options.metadir)

    -- now split the batch into fixed sizes called "minibatches" - these are the
    -- stories that are trained together in a single train step
    local minibatches = _.partition(batch, options.minibatchsize)
    local minibatchIdx = 0
    local minibatchCount = math.ceil(#batch / options.minibatchsize)
    for minibatch in minibatches do
      minibatchIdx = minibatchIdx + 1
      logger:info("[TRAIN] epoch %d; batch %d; minibatch %d/%d; training %d stories", 
        epochNumber, batchIdx, minibatchIdx, minibatchCount, #minibatch)
      --run the optimizer through one step (our minibatch) (this is where the magic happens)
      optimizeStep(function() return trainMinibatch(minibatch) end)
      collectgarbage() -- eh. why not?
    end

    logger:info("completed with batch - saving model")
    network.save(base.network, options, epochNumber, batchIdx, options.modeldir)
  end
  
  epochNumber = epochNumber + 1
end