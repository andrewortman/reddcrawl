local nngraph = require "nngraph"
local nn = require "nn"
local lfs = require "lfs"
local network = {}


-- create some styles to use when annotating our network
local styleGRU = {style='filled', fillcolor='#99D6AD'}
local styleDropout = {style="filled", fillcolor="#CCCCCC"}
local styleInput = {style='filled', fillcolor='#FFADEB'}
local styleInputHidden = {style='filled', fillcolor='#FFFFCA'}
local styleLinear = {style='filled', fillcolor='#CCB2FF'}

-- my implementation of a gated-recurrent-unit layer (GRU)
-- only takes size, the input's size needs to match the rnn size
-- use a Linear layer before this layer to accomplish this if needed
local function gru(size, gateSquash, dropoutRate)
	if gate_squash == nil then
		gate_squash = 1
	end

	local input = nn.Identity()():annotate{name="input", graphAttributes=styleInput}
	local previous = nn.Identity()():annotate{name="previous", graphAttributes=styleInputHidden}

	local input_sum = function() 
		local dropoutInput = nn.Dropout(dropoutRate)(input):annotate{name="dropout_input", graphAttributes=styleDropout}
		local dropoutPrevious = nn.Dropout(dropoutRate)(previous):annotate{name="dropout_previous", graphAttributes=styleDropout}
		local gate = nn.CAddTable()({
			nn.Linear(size, size)(dropoutInput):annotate{name="input_transform", graphAttributes=styleLinear},
			nn.Linear(size, size)(dropoutPrevious):annotate{name="prev_state_transform", graphAttributes=styleLinear}
		}):annotate{name="input_sum"}

		return gate
	end

	-- generate the reset and dynamic gates
	local reset = nn.Sigmoid()(nn.MulConstant(gateSquash, true)(input_sum())):annotate{name="reset_gate", graphAttributes=styleGRU}
	local dynamic = nn.Sigmoid()(nn.MulConstant(gateSquash, true)(input_sum())):annotate{name="dynamic_gate", graphAttributes=styleGRU}

	-- compute the candidate hidden value
	local candidate = nn.Tanh()(nn.CAddTable()({
		nn.Linear(size, size)(input), --it is the sum of the input
		nn.Linear(size, size)(nn.CMulTable()({ -- and the previous hidden value (limited by the reset gate (which emits a value 0->1))
			reset,
			previous
		}))
	})):annotate{name="candidate_cell", graphAttributes=styleGRU}

	-- the hidden output is now just the weighted sum of the candidate cell and the previous cell
	-- with the weight determined by the dynamic gate
	-- so out = d*candidate + (1-d)*previous
	local out = nn.CAddTable()({
		nn.CMulTable()({
			dynamic,
			candidate
		}),
		nn.CMulTable()({
			nn.MulConstant(-1)(nn.AddConstant(-1)(dynamic)),  -- simply 1-d where d is the dynamic gate output from 0->1
			previous
		})
	})

	local module = nn.gModule({input, previous}, {out})
	lfs.mkdir("network")
	graph.dot(module.fg, "fg", "network/gru.fg")
	graph.dot(module.bg, "bg", "network/gru.bg")
	return module;
end

-- create a single network
function network.create(rnnSize, rnnLayers, dropoutRate, gateSquash)
	local seqInputSize = 3
	local metadataInputSize = 1
	local outputSize = 2

	local inputScalingFactors = torch.Tensor{timestampScaling, scoreScaling, commentScaling}

	-- history is history inputs that change on each timestep
	local history = nn.Identity()():annotate{graphAttributes=styleInput}

	-- metadata doesnt change between calls to the network, so we seperated it as a second input
	local metadata = nn.Identity()():annotate{graphAttributes=styleInput} --seperated it out

	local inputTable = {history, metadata}

	-- first layer transforms the input and spreads it out to the RNN size... the rnn layer needs the same number of inputs as nodes
	local l1 = nn.Linear(seqInputSize+metadataInputSize, rnnSize)(nn.JoinTable(1)(inputTable)):annotate{name="input_transform", graphAttributes=styleLinear}

	-- generate the recurrent layers
	local previousLayer = l1
	local outputTable = {}
	for i = 1, rnnLayers do
		--prev h is an input to the layer - it is the output from this layer the last time it ran
		local prevH = nn.Identity()():annotate{name="prev_h["..i.."]", graphAttributes=styleInputHidden}
		table.insert(inputTable, prevH)

		--generate a GRU layer, and make its output part of the network's output (so we can use it again)
		local gruLayer = gru(rnnSize, gateSquash, dropoutRate)({previousLayer, prevH}):annotate{name="gru["..i.."]", graphAttributes=styleGRU}
		table.insert(outputTable, gruLayer)
		previousLayer = gruLayer
	end

	-- output layers linear transforms
	local outputLinear1 = nn.Tanh()(nn.Linear(rnnSize, rnnSize)(previousLayer):annotate{name="out_l1", graphAttributes=styleLinear})
	local outputLinear2 = nn.Linear(rnnSize, outputSize)(outputLinear1):annotate{name="out_l2", graphAttributes=styleLinear}
	table.insert(outputTable, 1, outputLinear2)

	local module = nn.gModule(inputTable, outputTable)
	-- module.verbose = true
	lfs.mkdir("network")
	graph.dot(module.fg, "fg", "network/fg")
	graph.dot(module.bg, "bg", "network/bg")
	return module
end

-- creates lots of clones of a network that share the same parameter and gradient memory space
-- updating the parameter on one will update the parameter on all clones
-- this allows each network to maintain a seperate state while sharing the same parameter space
function network.clone(network, copies)
	local clones = {}

	for i = 1, copies do
		-- this makes a clone but shares the weight, bias, gradWeight, and gradBias parameters
		clones[i] = network:clone('weight', 'bias', 'gradWeight', 'gradBias')
	end

	return clones
end

function network.save(network, options, epoch, batch, directory) 
	local filename = string.format("%s/e%03db%03d.model", directory, epoch, batch)
	lfs.mkdir(directory) -- make the models directory if it doesnt exist yet

	-- create our model information
	local savedModel = {options=options, network=network:clone():double(), epoch=epoch, batch=batch}
	torch.save(filename, savedModel)

	collectgarbage()
end

function network.load(filename)
	return torch.load(filename)
end

return network
