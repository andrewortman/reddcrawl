require "nngraph"
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
cmd:option("--gpuid", 0, "data directory")
options = cmd:parse(arg);

print("Creating network..")
local baseNetwork = network.create(options.rnnsize, options.rnnlayers, options.dropout)

local zeroedHiddenStateInputs = {}
for i = 1, options.rnnlayers do
	zeroedHiddenStateInputs[i] = torch.Tensor(options.rnnsize):zero()
end

print("Cloning network " .. options.seqlength .. " times..")
local clones = network.clone(baseNetwork, options.seqlength)

local batchFiles = loader.getFileListing(options.datadir)
print("Saw " .. #batchFiles .. " batch files.")
for idx = 1, #batchFiles do
	local batchFile = batchFiles[idx]
	print("Loading batch '" .. batchFile .. "' - this might take a minute")
	local batch = loader.loadBatch(batchFile)

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
		
		--now, we will handle each slice seperately
		lastHiddenStateInput = zeroedHiddenStateInputs --we need to track the last input state, since this is the beginning we'll make that the default 'zeroed' state
		for sliceNum = 0,numSlices-1 do
			for sample = 0,sliceSize-1 do
				local sampleIdx = sliceNum*sliceSize + sample + 1
				local input = {story.history[sampleIdx], unpack(lastHiddenStateInput)}
				print(input)
			end
		end

			
	end
	

end