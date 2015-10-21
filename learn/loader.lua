local cjson = require "cjson"

local loader = {}

local function historyLerp(history, startIdx, secondsOut)
	local last = {}
	last.timestamp = history["timestamp"][startIdx]
	last.score = history["score"][startIdx]
	last.comments = history["comments"][startIdx]
	local targetTimestamp = last.timestamp + (secondsOut * 1000)

	for idx = startIdx,#history["timestamp"] do
		local new = {}
		new.timestamp = history["timestamp"][idx]
		new.score = history["score"][idx]
		new.comments = history["comments"][idx]

		if new.timestamp > targetTimestamp then
			-- once we loop over past the target timestamp (start's history time + secondsOut)
			-- we do a linear interpolation to predict the difference
			-- between the start history and the target history time
			local timeDelta = (new.timestamp - last.timestamp)
			local scoreSlope = (new.score - last.score) / timeDelta
			local commentSlope = (new.comments - last.comments) / timeDelta 

			local prediction = {}
			prediction.score = (scoreSlope * (targetTimestamp - last.timestamp))
			prediction.comments = (commentSlope * (targetTimestamp - last.timestamp))
			return prediction
		end
		last = new
	end

	-- we couldn't determine the expected value because we ran out of history
	return nil
end

function loader.loadBatch(filename)
	local fp = io.open(filename, "r")
	if fp == nil then
		-- if cannot open, just return nil
		return nil
	end

	local storyList = {}
	for line in fp:lines() do 
		local decoded = cjson.decode(line)
		local historyCount = #decoded["history"]["timestamp"]

		-- create an object for each story that contains the following
		-- keys:
		--  * history - the matrix of history-based data to our model
		--  * metadata - metadata associated with the story used as input into the model
		--  * expected - the matrix of expected prediction values (outputs) of our model
		local story = {}
		 --delta time, delta score, delta comments
		story.history = torch.Tensor(historyCount, 3)
		-- 2 minute prediction for history for now
		story.expected = torch.Tensor(historyCount, 2) 
		story.metadata = {} -- nothing in metadata yet

		 -- I calculate deltas instead of absolute values as in history input matrix
		local last = {}
		last.timestamp = decoded["summary"]["createdAt"]
		last.score = 0
		last.comments = 0

		for x = 1, historyCount do
			local timestamp = decoded["history"]["timestamp"][x]
			local score = decoded["history"]["score"][x]
			local comments = decoded["history"]["comments"][x]

			--number of minutes since last update
			story.history[x][1] = (timestamp - last.timestamp)/60000.0
			
			--delta score/comments
			story.history[x][2] = score - last.score
			story.history[x][3] = comments - last.comments

			local nextExpected = historyLerp(decoded["history"], x, 60*2) 
			if nextExpected == nil then
				-- we can't predict any further, so we should trim the history 
				story.history:resize(x-1, 3)
				story.expected:resize(x-1, 2)
				break
			end

			story.expected[x][1] = nextExpected.score
			story.expected[x][2] = nextExpected.comments

			last.timestamp = timestamp
			last.score = score
			last.comments = comments
		end

		local set = decoded["set"]
		if storyList[set] == nil then
			storyList[set] = {}
		end
		table.insert(storyList[set], story)
	end
	return storyList
end

sets = loader.loadBatch("./data/test")
print("train: " .. #sets.train)
print("test: " .. #sets.test)
return loader