local nn = require "nn"
local nngraph = require "nngraph"
local optim = require "optim"
local lfs = require "lfs"
local network = require "network"
local datautil = require "datautil"
local logger = require "logger"
local dbg = require("debugger")


--- set up command line arguments
local cmd = torch.CmdLine()
cmd:text("reddcrawl-learn training script")
cmd:text("options:")
cmd:option("--rnnsize", 512, "size of each RNN layer")
cmd:option("--rnnlayers", 2, "number of RNN layers")
cmd:option("--dropout", 0.0, "dropout ratio (set to 0 to disable)")
cmd:option("--seqlength", 128, "sequence length for training")
cmd:option("--gradclamp", 16, "max abs value of a gradient ( set to 0 to disable )")
cmd:option("--minibatchsize", 8, "number of stories per training batch")
cmd:option("--datadir", "./data", "batch file directory")
cmd:option("--cachedir", "./traincache", "caching directory for preprocessed batch files")
cmd:option("--modeldir", "./models", "output models directory")
cmd:option("--gpuid", 0, "which gpu to use (set to 0 to use CPU)")
cmd:option("--initrange", 0.2, "uniform random numbers to set the weights")
cmd:option("--maxepochs", 0, "max number of epochs to run")
cmd:option("--gatesquash", 3, "factor to squash the GRU reset/dynamics gate")
cmd:option("--loadmodel", "", "if specified, a model to start with instead of creating a fresh one (all options must be the same!!)")
cmd:option("--reset", false, "if a model was loaded, set this flag to start the batch number back to zero")
cmd:option("--graph", true, "whether or not to show the graphs during training")

local options = cmd:parse(arg);

if options.gpuid ~= 0 then
	-- include cuda torch and cuda nn implementations if we are using the gpu
	require "cutorch"
	require "cunn"
end

if options.graph then
	require "gnuplot"
end

-- seed the RNG
torch.seed()

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
base.criterion = nn.MSECriterion()

-- ship network and criterion to GPU if needed
if options.gpuid ~= 0 then
	base.network = base.network:cuda()
	base.criterion = base.criterion:cuda()
end	
base.params, base.gradParams = base.network:getParameters() --we need to get the flattened parameters BEFORE we clone
if options.loadmodel == "" then
	logger:info("initializing params uniformly between +/-%f", options.initrange)
	base.params:uniform(-options.initrange, options.initrange) -- init the parameters to be uniformly randomly distirbuted
end
logger:info("the network has a total of %d parameters.", base.params:size(1))


-- next, we need to clone the network to a number of fixed steps
-- each network clone shares the same parameters and gradients, but each module in the network 
-- can have different gradInput and output caches
logger:info("cloning network %d times.. this may take a minute", options.seqlength)
local clones = network.clone(base.network, options.seqlength)

-- during forward prop, we need a table of tensors that represent the RNN state for the first sample 
-- and during backward prop, we need a table of tensors to represent the gradient of the rnn state for the last step
-- these can both be satisfied by a list of zeroed tensors
local zeroedRNNStates = {}
for i = 1, options.rnnlayers do
	if options.gpuid ~= 0 then
		zeroedRNNStates[i] = torch.CudaTensor(options.rnnsize):zero()
	else
		zeroedRNNStates[i] = torch.Tensor(options.rnnsize):zero()
	end
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
		if base.params ~= newParameters then
			base.params:copy(newParameters)
		end
		base.gradParams:zero()

		logger:info("ready for next minibatch")
		-- now call the function that actually does the train step
		local loss, steps = trainStep()
		logger:info("minibatch train complete - completed %d steps with an average loss of %f", steps, loss)

		base.gradParams:div(steps)

		if options.gradclamp > 0 then
			logger:info("clamping gradients to +/- %f; current peaks: %f, %f", options.gradclamp, base.gradParams:min(), base.gradParams:max())
			base.gradParams:clamp(-options.gradclamp, options.gradclamp)
		end

		-- gnuplot.figure(2)
		-- gnuplot.plot(torch.Tensor(base.params:size(1)):linspace(1, base.params:size(1), base.params:size(1)), base.params, '|')
		-- gnuplot.title('parameters')
		-- gnuplot.figure(3)
		-- gnuplot.plot(torch.Tensor(base.params:size(1)):linspace(1, base.params:size(1), base.params:size(1)), base.gradParams, '|')
		-- gnuplot.title('gradparams: '..tostring(base.gradParams:norm()))

		logger:info("performing optimize step on %d parameters", base.params:size(1))
		-- optim requires feval to return the loss and the gradient with respect to the loss
		return loss, base.gradParams
	end

	-- use rmsprop (todo: make this configurable?)
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
	epochNumber = epochNumber
	logger:info("starting epoch #%d", epochNumber)
	-- lets iterate through a single batch
	local startBatch = 1
	if options.reset == false and base.loadedModel ~= nil then
		startBatch = base.loadedModel.batch + 1
		logger:info("skipping to batch #%d", startBatch)
	end

	for batchIdx = startBatch, #batchFiles do
		local batchFile = batchFiles[batchIdx]
		logger:info("loading batch file '%s'", batchFile)
		--todo: do some optimization here (serialize & cache after the first prepro)
		local batch = datautil.loadBatch(batchFile, options.cachedir, options.gpuid ~= 0)

		-- now split the batch into fixed sizes called "minibatches" - these are the
		-- stories that are trained together in a single train step
		local numMiniBatches = math.floor(#batch / options.minibatchsize)
		for miniBatchIdx = 0, numMiniBatches-1 do
			-- create a function that we pass to the optimizer which does the actual forward/backward prop
			local function trainStep() 	
				-- calculate the bounds of the minibatch (story start / end)
				local miniBatchStartIdx = (miniBatchIdx * options.minibatchsize) + 1
				local miniBatchLoss = 0
				local miniBatchSamplesProcessed = 0 
				--todo: make this more readable
				for storyIdx = miniBatchStartIdx, miniBatchStartIdx+options.minibatchsize-1 do
					-- train function
					local story = batch[storyIdx]
					logger:info("[TRAIN]: epoch " .. epochNumber .. "; batch " .. batchIdx .. "; minibatch " .. miniBatchIdx+1 .. "; story ".. storyIdx .. "/" .. numMiniBatches * options.minibatchsize)

					--stories have an arbitrary number of history items, so we need to 
					--chunk them up into the smallest number of equal length slices as possible
					--there will be some clipped off at the end, but that is ok
					local numSlices = math.ceil(story.size/options.seqlength)
					local sliceSize = math.floor(story.size/numSlices)

					-- RNN outputs between time steps
					local netStates = {[0] = zeroedRNNStates}
					-- Actual outputs between time steps
					local netPredictions = {}

					-- for graphing
					local graphTimestamp = torch.Tensor(numSlices * sliceSize)
					local graphScoreExpected = torch.Tensor(numSlices * sliceSize)
					local graphScorePredicted = torch.Tensor(numSlices * sliceSize)

					-- for each slice in the stories history
					for sliceNum = 0,numSlices-1 do 
						-- forward prop through each sample, storing the full input and the full output into the inputs/ouputs table
						for sample = 1,sliceSize do 
							 -- kick the clone into training mode (enables the optional dropout)
							clones[sample]:training()	

							local sampleIdx = sliceNum*sliceSize + sample
							local input = {story.history[sampleIdx], story.metadata, unpack(netStates[sample-1])}
							local output = clones[sample]:forward(input) --the entire net output (the predictions + rnn state output)
							local expected = story.expected[sampleIdx]
							
							-- for graphing
							graphTimestamp[sampleIdx] = story.history[sampleIdx][1]
							graphScoreExpected[sampleIdx] = expected[1] 
							graphScorePredicted[sampleIdx] = output[1][1]

							-- store the output split into the rnn output and the rnn state into two different tables
							netPredictions[sample] = output[1]
							netStates[sample] = {}
							for i = 1, options.rnnlayers do
								netStates[sample][i] = output[i+1] 
							end

							--sum to the mean loss with the criterion's error
							miniBatchLoss = miniBatchLoss + base.criterion:forward(output[1], expected)
						end

						--keeps track of the gradients of the output - since we start backwards our last gradient is all zero for the RNN states
						local lastRNNStateGradients = zeroedRNNStates
						for sample = sliceSize, 1, -1 do 	
							-- we need to backprop (backwards)
							-- backwards requires the input and the gradient of the output
							-- the input is easy - it is just the sample + the previous' sample rnn state 
							-- the gradient of the output is simply the gradient of the output (calculated by the criterion) + the gradient of the next input (which is the output)
							local sampleIdx = sliceNum*sliceSize + sample
							local input = {story.history[sampleIdx], story.metadata, unpack(netStates[sample-1])}
							local output = netPredictions[sample]
							local expected = story.expected[sampleIdx]
							--calculate the prediction gradient
							local predictionGradient = base.criterion:backward(output, expected)

							--then we can backprop, which returns the input gradient with respect to the error
							local inputGradient = clones[sample]:backward(input, {predictionGradient, unpack(lastRNNStateGradients)})
							lastRNNStateGradients = {}
							for i = 3, #inputGradient do
								table.insert(lastRNNStateGradients, inputGradient[i])
							end
						end

						-- move the last rnn state to be the new initial state
						netStates[0] = netStates[#netStates]
					end

					gnuplot.figure(1)
					gnuplot.plot({"story", graphTimestamp, graphScoreExpected,'-'}, {"predicted", graphTimestamp, graphScorePredicted,'-'})
					gnuplot.title("output view")
					gnuplot.figure(2)
					local err = graphScorePredicted:clone():add(graphScoreExpected:clone():mul(-1)):abs()
					gnuplot.plot({"error", graphTimestamp, err,'|'})
					gnuplot.title("error view")
					gnuplot.axis({0, graphTimestamp:max(),0,1000})

					miniBatchSamplesProcessed = miniBatchSamplesProcessed + (numSlices * sliceSize)
				end

				--end of the minibatch - lets calculate the mean loss
				local meanLoss = miniBatchLoss / miniBatchSamplesProcessed
				return meanLoss, miniBatchSamplesProcessed
			end

			--run the optimizer through one step (our minibatch)
			optimizeStep(trainStep)
			collectgarbage()
		end

		logger:info("completed with batch - saving model")
		network.save(base.network, options, epochNumber, batchIdx, options.modeldir)
	end
end