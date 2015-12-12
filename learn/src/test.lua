local nn = require "nn"
local nngraph = require "nngraph"
local optim = require "optim"
local lfs = require "lfs"
local network = require "network"
local datautil = require "datautil"
local logger = require "logger"
local dbg = require "lib/debugger"
local _ = require "moses"

require "cutorch"
require "cunn"

require "gnuplot"

--- set up command line arguments
local cmd = torch.CmdLine()
cmd:text("reddcrawl-learn training script")
cmd:text("options:")
cmd:option("--data", "./data/test/part-00000.gz", "test batch file to load")
cmd:option("--meta", "./data/meta", "test metadata file to load")
cmd:option("--cache", "./cache/test", "where to cache the prepro data file")
cmd:option("--model", "", "what model to test against")
cmd:option("--gpuid", 1, "which gpu to use (set to 0 to use CPU)")
cmd:option("--out", "", "where to store the result (csv file)")
cmd:option("--graph", false, "graph results as they happen?")

local options = cmd:parse(arg);

if options.data == "" or options.model == "" or options.out == "" then
    logger:error("specify  --data, --out, and --model. Use --help for help")
    os.exit(1)
end

local outputFile = io.open(options.out, "w")

logger:info("loading model '%s' from disk..", options.model)
local loadedModel = network.load(options.model)
local network = loadedModel.network
local params, gradParams = network:getParameters()
logger:info("the network has a total of %d parameters.", params:size(1)) 

local criterion = nn.MSECriterion()

network = network:cuda()
criterion = criterion:cuda()

-- ensure we are in evaluation mode
network:training()

-- during forward prop, we need a table of tensors that represent the RNN state for the first sample 
local zeroedRNNStates = {}
local standardGradient = {torch.CudaTensor(1):fill(1), torch.CudaTensor(loadedModel.options.rnnsize):fill(0)}

for i = 1, loadedModel.options.rnnlayers do
    zeroedRNNStates[i] = torch.CudaTensor(loadedModel.options.rnnsize):zero()
end

logger:info("loading batch - this might take a minute if not cached")
local batch = datautil.loadBatch(options.data, options.cache, options.meta)
local lossSum = 0
local lossCount = 0
local losses = {}
for idx, story in pairs(batch) do
    logger:info("testing against story %d/%d", idx, #batch)
    gpuStory = datautil.copyStoryToGpu(story)

    local history = gpuStory.history
    local metadata = gpuStory.metadata
    local expected = gpuStory.expected


    local lastRNNState = {}
    for i = 1, #zeroedRNNStates do
        table.insert(lastRNNState, zeroedRNNStates[i]:clone())
    end
    
    local storyLoss = 0

    --graphing
    local gradNorms = {} 
    local labels = {"input", "timeofweek", "subreddit", "author", "flags", "previous"}
    for i = 1, 6 do 
        table.insert(gradNorms, torch.Tensor(history:size(1)):zero())
    end

    local graphTimestamps = torch.Tensor(history:size(1)):zero()
    local graphExpected = torch.Tensor(history:size(1)):zero()
    local graphPredicted = torch.Tensor(history:size(1)):zero()

    for sampleIdx = 1, history:size(1) do 
        local input = {gpuStory.history[sampleIdx]}
        input = _.append(input, gpuStory.metadata)
        input = _.append(input, lastRNNState)

        local output = network:forward(input)
        local expected = expected[sampleIdx]

        if options.graph then
            network:zeroGradParameters()
            local inputGrad = network:backward(input, standardGradient)
            local normSum = 0
            for i = 1, #inputGrad do
                normSum = normSum + inputGrad[i]:norm()
            end

            for i = 1, #inputGrad do
                gradNorms[i][sampleIdx] = inputGrad[i]:norm() / normSum
            end
        end

        graphTimestamps[sampleIdx] = history[sampleIdx][1]
        graphExpected[sampleIdx] = expected[1]
        graphPredicted[sampleIdx] = output[1][1]


        local loss = criterion:forward(output[1], expected)
        storyLoss = storyLoss + loss

        for i = #output-#story.metadata+1, #output do 
            lastRNNState[i-1]=output[i]
        end
    end
    lossSum = lossSum + storyLoss
    lossCount = lossCount + history:size(1)
    logger:info("story loss: %f", storyLoss/history:size(1))
    outputFile:write(string.format("%d,%d,%f\n", idx, history:size(1), storyLoss))
    outputFile:flush()
    table.insert(losses, storyLoss)

    if options.graph then
        gnuplot.figure(1)
        gnuplot.plot({'story', graphTimestamps, graphExpected, '-'}, {'predicted', graphTimestamps, graphPredicted, '-'})
        gnuplot.title("story view")

        gnuplot.figure(2)
        local views = {}
        for i = 1, #gradNorms do
            table.insert(views, {labels[i], graphTimestamps, gradNorms[i], '-'})
        end
        gnuplot.plot(views)
        gnuplot.title('grad norms')
    end
end

outputFile:close()

logger:info("total story loss: %f", lossSum/lossCount)