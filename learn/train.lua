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

local baseNetwork = network.create(options.rnnsize, options.rnnlayers, options.dropout)
local batchFiles = loader.getFileListing(options.datadir)
print("Saw " .. #batchFiles .. " batch files.")
for idx = 1, #batchFiles do
	local batchFile = batchFiles[idx]
	print("Loading batch '" .. batchFile .. "' - this might take a minute")
	local batch = loader.loadBatch(batchFile)
	print("Batch loading complete")
end

-- graph.dot(mlp.fg, 'network', 'network')