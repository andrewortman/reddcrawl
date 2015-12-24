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
-- todo(way future) - make this an actual nn/cunn module so it doesnt have to do a lot of sequential steps to do layer
local function gru(rnnSize, gateSquash, dropoutRate)
  if gate_squash == nil then
  gate_squash = 1
  end

  local input = nn.Identity()():annotate{name="input", graphAttributes=styleInput}
  local previous = nn.Identity()():annotate{name="previous", graphAttributes=styleInputHidden}

  local input_sum = function() 
  local gate = nn.CAddTable()({
    nn.Linear(rnnSize, rnnSize)(input):annotate{name="input_transform", graphAttributes=styleLinear},
    nn.Linear(rnnSize, rnnSize)(previous):annotate{name="prev_state_transform", graphAttributes=styleLinear}
  }):annotate{name="input_sum"}

  return gate
  end

  -- generate the reset and dynamic gates
  local reset = nn.Sigmoid()(nn.MulConstant(gateSquash, true)(input_sum())):annotate{name="reset_gate", graphAttributes=styleGRU}
  local dynamic = nn.Sigmoid()(nn.MulConstant(gateSquash, true)(input_sum())):annotate{name="dynamic_gate", graphAttributes=styleGRU}

  -- compute the candidate hidden value
  local candidate = nn.Tanh()(nn.CAddTable()({
      nn.Linear(rnnSize, rnnSize)(input):annotate{graphAttributes=styleLinear}, --it is the sum of the input
      nn.Linear(rnnSize, rnnSize)(nn.CMulTable()({ -- and the previous hidden value (limited by the reset gate (which emits a value 0->1))
        reset,
        previous
      })):annotate{graphAttributes=styleLinear}
  })):annotate{name="candidate_cell", graphAttributes=styleGRU}

  -- the hidden output is now just the weighted sum of the candidate cell and the previous cell
  -- with the weight determined by the dynamic gate
  -- so out = d*candidate + (1-d)*previous
  local out = nn.CAddTable()({
      nn.CMulTable()({
        nn.MulConstant(-1)(nn.AddConstant(-1)(dynamic)), -- simply 1-d where d is the dynamic gate output from 0->1
        candidate
      }),
      nn.CMulTable()({
        dynamic,  
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
  -- these input sizes must match up with what is fed into the network!
  local seqInputSize = 3 -- timestamp, history, comments
  local metadataTimeOfWeekSize = 1 -- time of week (0->1)
  local metadataSubredditOneHotSize = 50 -- one hot of subreddits
  local metadataAuthorRankSize = 6 -- author ranks: top 5, 10, 50, 100, 500, 1000
  local metadataDomainRankSize = 6 -- domain ranks: top 5, 10, 50, 100, 500, 1000
  local metadataStoryFlagsSize = 3 -- has thumbnail, is nsfw, is self
  local outputSize = 1 --just score for now

  -- history is history inputs that change on each timestep
  local history = nn.Identity()():annotate{name="story_history_input", graphAttributes=styleInput}

  -- metadata doesnt change between calls to the network, so we seperated it as a second input
  local metadataTimeOfWeek = nn.Identity()():annotate{name="metadata_timeofweek", graphAttributes=styleInput}
  local metadataSubredditOneHot = nn.Identity()():annotate{name="metadata_subreddit_onehot", graphAttributes=styleInput}
  local metadataAuthorRank = nn.Identity()():annotate{name="metadata_authorrank", graphAttributes=styleInput}
  local metadataDomainRank = nn.Identity()():annotate{name="metadata_domainrank", graphAttributes=styleInput}
  local metadataStoryFlags = nn.Identity()():annotate{name="metadata_flags", graphAttributes=styleInput}

  local inputTable = {history, metadataTimeOfWeek, metadataSubredditOneHot, metadataAuthorRank, metadataDomainRank, metadataStoryFlags}
  local inputSize = seqInputSize + metadataTimeOfWeekSize + metadataSubredditOneHotSize + metadataAuthorRankSize + metadataDomainRankSize +  metadataStoryFlagsSize
  local inputTableJoined = nn.JoinTable(1)(inputTable):annotate{name="story_inputs_joined", graphAttributes=styleInputHidden}
  local inputTransformedLayer = nn.Linear(inputSize, rnnSize)(inputTableJoined):annotate{name="in_l1", graphAttributes=styleLinear}
  -- generate the recurrent layers
  local previousLayer = inputTransformedLayer

  local outputTable = {}
  for i = 1, rnnLayers do
      --prev h is an input to the layer - it is the output from this layer the last time it ran
      local prevH = nn.Identity()():annotate{name="prev_h["..i.."]", graphAttributes=styleInputHidden}
      table.insert(inputTable, prevH)

      local inputDropoutLayer = nn.Dropout(dropoutRate)(previousLayer):annotate{name="gru_input_dropout["..i.."]", graphAttributes=styleDropout}

      --generate a GRU layer, and make its output part of the network's output (so we can use it again)
      local gruLayer = gru(rnnSize, gateSquash, dropoutRate)({inputDropoutLayer, prevH}):annotate{name="gru["..i.."]", graphAttributes=styleGRU}
      table.insert(outputTable, gruLayer) -- do not dropout through time!

      previousLayer = gruLayer
  end

  -- output layers linear transforms
  local outputDropout = nn.Dropout(dropoutRate)(previousLayer)
  local outputLinear = nn.Linear(rnnSize, outputSize)(outputDropout):annotate{name="out_l1", graphAttributes=styleLinear}
  local outputReLU = nn.PReLU()(outputLinear)
  table.insert(outputTable, 1, outputReLU)

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

function network.save(network, options, optimConfig, optimState, epoch, batch, directory) 
  local filename = string.format("%s/e%03db%03d.model", directory, epoch, batch)
  lfs.mkdir(directory) -- make the models directory if it doesnt exist yet

  -- create our model information
  local savedModel = {options=options, optim={config=optimConfig, state=optimState}, network=network:clone():double(), epoch=epoch, batch=batch}
  torch.save(filename, savedModel)

  collectgarbage()
end

function network.load(filename)
  return torch.load(filename)
end

return network
