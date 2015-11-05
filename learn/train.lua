require "nngraph"
require "cutorch"
require "cunn"
require "optim"
local lfs = require "lfs"
local network = require "network"
local loader = require "loader"

--- set up command line arguments
cmd = torch.CmdLine()
cmd:text("reddcrawl-learn training script")
cmd:text("options:")
cmd:option("--rnnsize", 128, "size of each RNN layer")
cmd:option("--rnnlayers", 2, "number of RNN layers")
cmd:option("--dropout", 0.0, "dropout ratio (set to 0 to disable)")
cmd:option("--seqlength", 64, "sequence length for training")
cmd:option("--gradclamp", 64, "max abs value of a gradient")
cmd:option("--batchsize", 128, "number of stories per training batch")
cmd:option("--datadir", "./data", "data directory")
cmd:option("--gpuid", 0, "which gpu to use (set to 0 to use CPU)")
cmd:option("--initrange", 0.05, "uniform random numbers to set the weights")
options = cmd:parse(arg);

print("Creating network..")

local base = {}
base.network = network.create(options.rnnsize, options.rnnlayers, options.dropout)
base.criterion = nn.MSECriterion()
if options.gpuid ~= 0 then
	base.network = base.network:cuda()
	base.criterion = base.criterion:cuda()
end	
base.params, base.gradParams = base.network:getParameters() --we need to get the flattened parameters BEFORE we clone
base.params:uniform(-options.initrange, options.initrange) -- init the parameters to be uniformly randomly distirbuted

-- during forward prop, we need a table of tensors that represent the RNN state for the sample 
-- and during backward prop, we need a table of tensors to represent the gradient of the rnn state for the future
-- these can both be satisfied by a list of zerod tensors (the first RNN state can be zero) and the calculated output gradient
-- of the last output can also be zero
local zeroedRNNStates = {}
for i = 1, options.rnnlayers do
	if options.gpuid ~= 0 then
		zeroedRNNStates[i] = torch.CudaTensor(options.rnnsize):zero()
	else
		zeroedRNNStates[i] = torch.Tensor(options.rnnsize):zero()
	end
end

-- next, we need to clone the network to a number of fixed steps
-- this is for optimization reasons - each network holds state in order 
-- to compute the gradients faster on backproprogation
print("Cloning network " .. options.seqlength .. " times..")
local clones = network.clone(base.network, options.seqlength)

-- get a list of batch files
local batchFiles = loader.getFileListing(options.datadir)
print("Saw " .. #batchFiles .. " batch files.")

local optimState = {}
local optimConfig = {
}

local epochNumber = 0
while true do
	epochNumber = epochNumber + 1
	-- for each batch file, do an optimization step
	for batchIdx = 1, #batchFiles do
		local batchFile = batchFiles[batchIdx]
		print("Loading batch '" .. batchFile)
		local batch = loader.loadBatch(batchFile, options.gpuid ~= 0)

		local numMiniBatches = math.floor(#batch.train / options.batchsize)

		for miniBatchIdx = 1, numMiniBatches do
			local function feval(newParameters) 
				if base.params ~= newParameters then
					base.params:copy(newParameters)
				end
				base.gradParams:zero()

				-- go through this "minibatch" of stories
				local miniBatchStartIdx = ((miniBatchIdx-1) * options.batchsize) + 1
				local miniBatchLoss = 0
				local miniBatchSamplesProcessed = 0 
				for storyIdx = miniBatchStartIdx, miniBatchStartIdx+options.batchsize-1 do
					-- train function
					local story = batch.train[storyIdx]
					print("[TRAIN]: epoch " ..epochNumber .. "; batch " .. batchIdx .. "; minibatch " .. miniBatchIdx .. "; story ".. storyIdx .. "/" .. numMiniBatches * options.batchsize)

					--stories have an arbitrary number of history items, so we need to 
					--chunk them up into the smallest number of equal length slices as possible
					--there will be some clipped off at the end, but that is ok
					local numSlices = math.ceil(story.size/options.seqlength)
					local sliceSize = math.floor(story.size / numSlices)

					-- state
					local netStates = {[0] = zeroedRNNStates}
					local netPredictions = {}

					-- for each slice in the stories history
					for sliceNum = 0,numSlices-1 do 
						-- forward prop through each sample, storing the full input and the full output into the inputs/ouputs table
						for sample = 1,sliceSize do 
							 -- kick the clone into training mode (enables the optional dropout)
							clones[sample]:training()				
							local sampleIdx = sliceNum*sliceSize + sample
							local input = {story.history[sampleIdx], unpack(netStates[sample-1])}
							local output = clones[sample]:forward(input) --the entire net output (the predictions + rnn state output)
							local expected = story.expected[sampleIdx]
							
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
							local input = {story.history[sampleIdx], unpack(netStates[sample-1])}
							local output = netPredictions[sample]
							local expected = story.expected[sampleIdx]
							--calculate the prediction gradient
							local predictionGradient = base.criterion:backward(output, expected)

							--then we can backprop, which returns the input gradient with respect to the error
							local inputGradient = clones[sample]:backward(input, {predictionGradient, unpack(lastRNNStateGradients)})
							lastRNNStateGradients = {}
							for i = 2, #inputGradient do
								table.insert(lastRNNStateGradients, inputGradient[i])
							end
						end

						-- move the last rnn state to be the new initial state
						netStates[0] = netStates[#netStates]
					end

					miniBatchSamplesProcessed = miniBatchSamplesProcessed + (numSlices * sliceSize)
				end

				--end of the minibatch - lets calculate the mean loss and do the gradparam clip
				local meanLoss = miniBatchLoss / miniBatchSamplesProcessed

				print("mean sample loss of minibatch: " .. meanLoss)
				return meanLoss, base.gradParams
			end

			optim.adam(feval, base.params, optimConfig, optimState)
		end
	end
end