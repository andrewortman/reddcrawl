require "nngraph"
require "cunn"
require "cutorch"
local network = {}

-- my implementation of a gated-recurrent-unit layer (GRU)
-- only takes size, the input's size needs to match the rnn size
-- use a Linear layer before this layer to accomplish this if needed
local function gru(size) 
	local input = nn.Identity()()
	local previous = nn.Identity()()

	-- basically a component-wise linear operator (instead of fully connected)
	-- todo: we might be able to do a parallel container instead, but check on that later
	-- to see if it event matters
	local input_sum = function() 
		local gate = nn.CAddTable()({
			nn.CMul(size)(input),
			nn.CMul(size)(previous)
		})

		-- add bias
		return nn.Add(size)(gate)
	end

	-- generate the reset and dynamic gates
	local reset = nn.Sigmoid()(input_sum())
	local dynamic = nn.Sigmoid()(input_sum())

	-- compute the candidate hidden value
	local candidate = nn.Tanh()(nn.Add(size)(nn.CAddTable()({
		nn.CMul(size)(input), --it is the sum of the input
		nn.CMulTable()({ -- and the previous hidden value (limited by the reset gate (which emits a value 0->1))
			reset,
			previous
		})
	})))

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

	return nn.gModule({input, previous}, {out})
end

-- create a single network
function network.create(rnnSize, rnnLayers, dropoutRate)
	local seqInputSize = 3
	local outputSize = 2

	-- create some styles to use when annotating our network
	local styleGRU = {style='filled', fillcolor='#99D6AD'}
	local styleDropout = {style="filled", fillcolor="#CCCCCC"}
	local styleInput = {style='filled', fillcolor='#FFADEB'}
	local styleInputHidden = {style='filled', fillcolor='#FFFFCA'}
	local styleLinear = {style='filled', fillcolor='#CCB2FF'}

	--input the entire network - the first value of the input table is the actual input vector
	--and the remaining table values are the vectors representing the previous outputs from each hidden reccurrent layer
	local input = nn.Identity()():annotate{graphAttributes=styleInput}
	local inputTable = {input}

	-- first layer transforms the input and spreads it out to the RNN size... the rnn layer needs the same number of inputs as nodes
	local l1 = nn.Linear(seqInputSize, rnnSize)(input):annotate{name="input_transform", graphAttributes=styleLinear}
	local drop1 = nn.Dropout(dropoutRate)(l1):annotate{name="drop1", graphAttributes=styleDropout}

	-- generate the recurrent layers
	local previousLayer = drop1
	local outputTable = {}
	for i = 1, rnnLayers do
		--prev h is an input to the layer - it is the output from this layer the last time it ran
		local prevH = nn.Identity()():annotate{name="prev_h["..i.."]", graphAttributes=styleInputHidden}
		table.insert(inputTable, prevH)

		--generate a GRU layer, and make its output part of the network's output (so we can use it again)
		local gruLayer = gru(rnnSize)({previousLayer, prevH}):annotate{name="gru["..i.."]", graphAttributes=styleGRU}
		table.insert(outputTable, gruLayer)

		--after each gru layer, I'm going ahead and doing a linear "shuffle" as well as introducing a dropout layer for regularization
		local gruLinear = nn.Linear(rnnSize,rnnSize)(gruLayer):annotate{name="gru_l["..i.."]", graphAttributes=styleLinear}
		local dropoutLayer = nn.Dropout(dropoutRate)(gruLinear):annotate{name="drop_gru["..i.."]", graphAttributes=styleDropout}

		--make the input to the next layer the output of this one
		previousLayer = dropoutLayer
	end

	-- output layers
	out = nn.Linear(rnnSize, outputSize)(previousLayer):annotate{name="output_transform", graphAttributes=styleLinear}
	table.insert(outputTable, 1, out)

	return nn.gModule(inputTable, outputTable)
end

-- creates a new 1D tensor and points all the parameters to that tensor
-- this reallocates the parameter memory!!
function network.flatten(network)
	local function flatten(parameters) 
		local totalNumElements = 0
		local numElements = {}
		for idx, singleParameterTensor in pairs(parameters) do
			local tensorNumElements = singleParameterTensor:size(1)
			for i = 2,singleParameterTensor:dim() do
				tensorNumElements = tensorNumElements * singleParameterTensor:size(i)
			end

			numElements[idx] = tensorNumElements
			totalNumElements = totalNumElements + tensorNumElements
		end

		local flattenedTensor = torch.Tensor(totalNumElements)
		local currentOffset = 1
		for idx, singleParameterTensor in pairs(parameters) do 
			local copy = singleParameterTensor:clone(); -- since we are mutating the current parameter list, we need to take a clone  of the old values
			singleParameterTensor:set(flattenedTensor:storage(), currentOffset, singleParameterTensor:size())
			singleParameterTensor:copy(copy)
			currentOffset = currentOffset + numElements[idx]
		end
	end	
	local params, gradParams = network:parameters()
	return flatten(params, gradParams)
end

-- creates lots of clones of a network that share the same parameter and gradient memory space
-- updating the parameter on one will update the parameter on all clones
-- this allows each network to maintain a seperate state while sharing the same parameter space
function network.clone(network, copies)
	local clones = {}
	local baseParams, baseGradParams = network:parameters()
	for i = 1, copies do
		clones[i] = network:clone('weight', 'bias', 'gradWeight', 'gradBias')
	end

	return clones
end


return network