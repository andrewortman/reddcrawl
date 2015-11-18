local nn = require "nn"
local nngraph = require "nngraph"
local optim = require "optim"
local lfs = require "lfs"
local network = require "network"
local datautil = require "datautil"
local logger = require "logger"
local dbg = require("debugger")

require "gnuplot"

--- set up command line arguments
local cmd = torch.CmdLine()
cmd:text("reddcrawl-learn training script")
cmd:text("options:")
cmd:option("--data", "", "test batch file to load")
cmd:option("--cachedir", "./testcache", "where to cache the prepro data file")
cmd:option("--model", "", "what model to test against")
cmd:option("--gpuid", 0, "which gpu to use (set to 0 to use CPU)")
cmd:option("--out", "", "where to store the result (csv file)")

local options = cmd:parse(arg);

if options.data == "" or options.model == "" or options.out == "" then
	logger:error("specify  --data, --out, and --model. Use --help for help")
	os.exit(1)
end

if options.gpuid ~= 0 then
	-- include cuda torch and cuda nn implementations if we are using the gpu
	require "cutorch"
	require "cunn"
end

local outputFile = io.open(options.out, "w")

logger:info("loading model '%s' from disk..", options.model)
local loadedModel = network.load(options.model)
local network = loadedModel.network
local params, gradParams = network:getParameters()
logger:info("the network has a total of %d parameters.", params:size(1)) 

local criterion = nn.MSECriterion()

-- ship network and criterion to GPU if needed
if options.gpuid ~= 0 then
	network = network:cuda()
	criterion = criterion:cuda()
end	

-- ensure we are in evaluation mode
network:evaluate()

-- during forward prop, we need a table of tensors that represent the RNN state for the first sample 
local zeroedRNNStates = {}
for i = 1, loadedModel.options.rnnlayers do
	if options.gpuid ~= 0 then
		zeroedRNNStates[i] = torch.CudaTensor(loadedModel.options.rnnsize):zero()
	else
		zeroedRNNStates[i] = torch.Tensor(loadedModel.options.rnnsize):zero()
	end
end

logger:info("loading batch - this might take a minute if not cached")
local batch = datautil.loadBatch(options.data, options.cachedir, options.gpuid ~= 0)
local lossSum = 0
local lossCount = 0
local losses = {}
for idx, story in pairs(batch) do
	logger:info("testing against story %d/%d", idx, #batch)

	local history = story.history
	local expected = story.expected

	local lastRNNState = zeroedRNNStates

	local storyLoss = 0
	for sampleIdx = 1, history:size(1) do 
		local input = {history[sampleIdx], unpack(lastRNNState)}
		local output = network:forward(input)
		local expected = expected[sampleIdx]

		local loss = criterion:forward(output[1], expected)
		storyLoss = storyLoss + loss

		for i = 2, #output do 
			lastRNNState[i-1]=output[i]
		end
	end
	lossSum = lossSum + storyLoss
	lossCount = lossCount + history:size(1)
	logger:info("story loss: %f", storyLoss/history:size(1))
	outputFile:write(string.format("%d,%d,%f\n", idx, history:size(1), storyLoss))
	outputFile:flush()
	table.insert(losses, storyLoss)
end

outputFile:close()

logger:info("total story loss: %f", lossSum/lossCount)