require "nngraph"
network = require "network"



mlp = createNetwork(4, 1, 512, 2)


params,gradparams = mlp:parameters();

graph.dot(mlp.fg, 'network', 'network')
graph.dot(gru(512).fg, 'gru', 'gru')