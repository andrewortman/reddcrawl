require "nngraph"
require "nn"
require "optim"
require 'gnuplot'

sample = {}
sample.input = torch.Tensor(4098):linspace(0, 10, 4098)
sample.expected = sample.input:clone():sqrt()

print(sample.expected)

local function createNetwork() 
	local inp = nn.Identity()()
	local h1 = nn.Tanh()(nn.Linear(1, 32)(inp))
	local drop = nn.Dropout(0.1)(h1)
	local h2 = nn.Tanh()(nn.Linear(32, 32)(drop))
	local out = nn.Linear(32, 1)(h2)
	return nn.gModule({inp},{out})
end

local network = createNetwork()
local params, gradParams = network:getParameters()
local criterion = nn.MSECriterion()

local state ={}
for epoch = 1, 100 do
	print("Epoch " .. epoch)
	local function feval(x)  
		params:set(x)
		gradParams:zero()
		network:training()
		local loss = 0
		for i = 1, sample.expected:size(1) do
			-- forward prop
			local input = torch.Tensor{sample.input[i]}
			local output = network:forward(input)
			local expected = torch.Tensor{sample.expected[i]}
			loss = loss + criterion:forward(output, expected)
			-- backward prop
			local gradLoss = criterion:backward(output, expected)
			local gradInput = network:backward(input, gradLoss)
		end
		loss = loss/sample.expected:size(1)
		print ("Loss is " .. loss)
		return loss, gradParams
	end
	optim.rmsprop(feval, params, {learningRate=5e-3}, state)
	gnuplot.figure(1)
	gnuplot.title("preditions vs reality")

	local x = torch.Tensor():linspace(0, 10, 100)
	local y = torch.Tensor(100)
	local yy = torch.Tensor(100)
	for xx = 1, 100  do
		network:evaluate()
		y[xx] = network:forward(torch.Tensor{x[xx]})
		yy[xx] = math.sqrt(x[xx])
	end
	gnuplot.plot({x,y}, {x, yy})
end