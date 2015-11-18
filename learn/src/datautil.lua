local cjson = require "cjson"
local lfs = require "lfs"
local logger = require "logger"
local gzip = require "gzip"
local dbg = require "debugger"

local datautil = {}

datautil.MINUTES_PREDICTED = 10

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

function datautil.loadBatch(path, cachedir, useCuda)
	-- if we need to load into the GPU, we need to load cutorch
	if useCuda then
		require "cutorch"
	end

	-- lets check if the model was already cached
	local filename = path:match("([^/]+)$")
	
	local storyList = {}
	-- check if file exists
	local cachefilename = cachedir.."/"..filename
	local cachefp = io.open(cachefilename)
	if cachefp ~= nil then
		cachefp:close()

		--we can load from the cache
		logger:info("loading batch from cache located at '%s'", cachefilename)
		storyList = torch.load(cachefilename)
	else
		local fp = gzip.open(path, "r") --batch files are gziped
		if fp == nil then
			-- if cannot open, just return empty table and log
			logger:error("Cannot read batch file '%s'!", path)
			return {}
		end

		for line in fp:lines() do 
			local decoded = cjson.decode(line)
			local historyCount = #decoded["history"]["timestamp"]

			-- create an object for each story that contains the following keys:
			--  * history - the matrix of history-based data to our model
			--  * expected - the matrix of expected prediction values (outputs) of our model
			local story = {}
			 -- time,  score,  comments
			story.history = torch.Tensor(historyCount, 3)
			-- 2 minute prediction for history for now
			story.expected = torch.Tensor(historyCount, 2) 
			story.metadata = {} -- nothing in metadata yet


			--create metadata			
			local createdAt = decoded["summary"]["createdAt"]

			--moment of week is a scale from 0->1 where 0 is midnight on sunday, and 1 is 1 second before midnight on sunday
			local createdAtDate = os.date("*t", createdAt/1000.0)
			local secondOfDay = createdAtDate.hour*(60*60) + createdAtDate.min*(60) + createdAtDate.sec
			local momentOfWeek = ((createdAtDate.wday-1)*(60*60*24) + secondOfDay)/(60*60*24*7)
			story.metadata = torch.Tensor{momentOfWeek}

			local lastScore = 0
			local lastComments = 0

			for x = 1, historyCount do
				local timestamp = decoded["history"]["timestamp"][x]
				local score = decoded["history"]["score"][x]
				local comments = decoded["history"]["comments"][x]

				--number of hours since created
				story.history[x][1] = (timestamp - createdAt)/(60*60*1000.0)
				
				--score/comments
				story.history[x][2] = score
				story.history[x][3] = comments

				lastScore = score
				lastComments = comments

				--2 minutes ahead of time prediction
				local nextExpected = historyLerp(decoded["history"], x, 60*datautil.MINUTES_PREDICTED) 
				if nextExpected == nil then
					-- we can't predict any further, so we should trim the history 
					story.history:resize(x-1, 3)
					story.expected:resize(x-1, 2)
					story.size = x-1; -- store the size so it can be easily consumed
					break
				end

				story.expected[x][1] = (nextExpected.score + score)
				story.expected[x][2] = (nextExpected.comments + comments)
			end

			table.insert(storyList, story)
		end

		logger:info("caching %s to %s", filename, cachefilename)
		lfs.mkdir(cachedir) -- make the cache directory if it doesnt exist yet
		torch.save(cachefilename, storyList)
	end

	-- postprocess after load
	if useCuda then
		for idx, story in pairs(storyList) do
			storyList[idx].history = story.history:cuda()
			storyList[idx].expected = story.expected:cuda()
			storyList[idx].metadata = story.metadata:cuda()
		end
	end

	collectgarbage()
	return storyList
end

function datautil.getFileListing(directory) 
	local listing = {}
	for file in lfs.dir(directory) do
		local path = directory .. "/" .. file
		local attr = lfs.attributes(path)
		if attr.mode == "file" and attr.size > 0 then
			table.insert(listing, path)
		end
	end
	table.sort(listing)
	return listing
end

return datautil
