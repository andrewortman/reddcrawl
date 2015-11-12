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
			prediction.score = last.score + (scoreSlope * (targetTimestamp - last.timestamp))
			prediction.comments = last.comments + (commentSlope * (targetTimestamp - last.timestamp))
			return prediction
		end
		last = new
	end

	-- we couldn't determine the expected value because we ran out of history
	return nil
end

function loader.loadBatch(filename, useCuda)
	-- if we need to load into the GPU, we need to load cutorch
	if useCuda then
		require "cutorch"
	end

	local fp = io.open(filename, "r")
	if fp == nil then
		-- if cannot open, just return nil
		return nil
	end

	local storyList = {}
	for line in fp:lines() do 
		local decoded = cjson.decode(line)
		local historyCount = #decoded["history"]["timestamp"]

		-- create an object for each story that contains the following keys:
		--  * history - the matrix of history-based data to our model
		--  * expected - the matrix of expected prediction values (outputs) of our model
		local story = {}
		 --delta time, delta score, delta comments
		story.history = torch.Tensor(historyCount, 3)
		-- 2 minute prediction for history for now
		story.expected = torch.Tensor(historyCount, 2) 
		story.metadata = {} -- nothing in metadata yet

		 -- I calculate deltas instead of absolute values as in history input matrix
		local lastTimestamp = decoded["summary"]["createdAt"]

		for x = 1, historyCount do
			local timestamp = decoded["history"]["timestamp"][x]
			local score = decoded["history"]["score"][x]
			local comments = decoded["history"]["comments"][x]

			--number of minutes since last update
			story.history[x][1] = (timestamp - lastTimestamp)/60000.0
			
			--score/comments
			story.history[x][2] = score
			story.history[x][3] = comments

			--10 minutes ahead of time prediction
			local nextExpected = historyLerp(decoded["history"], x, 60*10) 
			if nextExpected == nil then
				-- we can't predict any further, so we should trim the history 
				story.history:resize(x-1, 3)
				story.expected:resize(x-1, 2)
				story.size = x-1; -- store the size so it can be easily consumed
				break
			end

			story.expected[x][1] = nextExpected.score
			story.expected[x][2] = nextExpected.comments

			lastTimestamp = timestamp
		end

		-- sort them into sets (train/test)
		local set = decoded["set"]
		if storyList[set] == nil then
			storyList[set] = {}
		end

		-- if we want to use cuda, we should ship it off to the GPU
		if useCuda then
			story.expected = story.expected:cuda()
			story.history = story.history:cuda()
		end

		table.insert(storyList[set], story)
		collectgarbage()
	end
	return storyList
end

function loader.getFileListing(directory) 
	local listing = {}
	for file in lfs.dir(options.datadir) do
		local path = directory .. "/" .. file
		local attr = lfs.attributes(path)
		if attr.mode == "file" and attr.size > 0 then
			table.insert(listing, path)
		end
	end
	table.sort(listing)
	return listing
end

return loader