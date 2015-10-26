require "nngraph"
require "cutorch"
require "cunn"
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
cmd:option("--batchsize", 128, "number of stories per training batch")
cmd:option("--datadir", "./data", "data directory")
cmd:option("--gpuid", 0, "which gpu to use (set to 0 to use CPU)")
options = cmd:parse(arg);

print("Creating network..")

local base = {}
base.network = network.create(options.rnnsize, options.rnnlayers, options.dropout)
if options.gpuid ~= 0 then
	base.network = base.network:cuda()
end	
base.params, base.gradParams = base.network:getParameters() --we need to get the flattened parameters BEFORE we clone
base.criterion = nn.MSECriterion()

local zeroedHiddenStateInputs = {}
for i = 1, options.rnnlayers do
	zeroedHiddenStateInputs[i] = torch.Tensor(options.rnnsize):zero()
	if options.gpuid ~= 0 then
		zeroedHiddenStateInputs[i] = zeroedHiddenStateInputs[i]:cuda()
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

-- for each batch file, do an optimization step
for idx = 1, #batchFiles do
	local batchFile = batchFiles[idx]
	print("Loading batch '" .. batchFile)
	local batch = loader.loadBatch(batchFile, options.gpuid ~= 0)

	for storyIdx = 1, #batch.train do
		local story = batch.train[storyIdx]
		print("Training on story " .. storyIdx .. " / " .. #batch.train)
		print(story)

		--stories have an arbitrary number of history items, so we need to 
		--chunk them up into the smallest number of equal length slices as possible
		local numSlices = math.ceil(story.size/options.seqlength)
		local sliceSize = math.floor(story.size / numSlices)
		local numStripped = story.size - (numSlices * sliceSize)
		print("There are " .. numSlices .. " sized slices each sized at " .. sliceSize .. " ; stripping " .. numStripped .. " off")
		
		base.gradParams:zero()
		local rnnStates = {[0] = zeroedHiddenStateInputs}
		local storyLoss = 0
		for sliceNum = 0,numSlices-1 do
			--forward prop this slice
			for sample = 1,sliceSize do
				clones[sample]:training() -- kick the clone into training mode
				local sampleIdx = sliceNum*sliceSize + sample
				local input = {story.history[sampleIdx], unpack(rnnStates[sample-1])}
				local out = clones[sample]:forward(input)

				rnnStates[sample] = {}
				for i = 1, options.rnnlayers do
					rnnStates[sample][i] = out[i+1] --rnn state is always after the output element, so we offset by one
				end

				storyLoss = storyLoss + base.criterion:forward(out[1], story.expected[sampleIdx])
			end

			--and then backward prop from the end to the start
		end
		storyLoss = storyLoss / (numSlices * sliceSize)
		print("story loss: " .. storyLoss)
	end
end