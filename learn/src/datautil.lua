local cjson = require "cjson"
local lfs = require "lfs"
local logger = require "logger"
local gzip = require "gzip"
local _ = require "moses"

local datautil = {}

datautil.SCORE_SCALE = (1/1000)
datautil.COMMENT_SCALE = (1/1000)
datautil.MINUTES_PREDICTED = 2
datautil.MAX_AUTHORS = 1000 -- max authors to consider when ranking
datautil.AUTHOR_RANKS = {5, 10, 50, 100, 500, 1000}
datautil.MAX_STORY_RETENTION_MS = (48*60*60*1000.0)

-- utility function that can calculate the value of a continuous linearly interpolated curve
-- of discrete samples at a given timestamp
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

-- utility function that will load the metadata from disk
local function loadMetadata(metadatadir) 
    --load metadata from disk   --the metadatadir contains two folders produced by our spark job:
    -- {metadatadir}/authors/part-00000: map of authors to the total link karma we saw from them (sorted)
    -- {metadatadir}/subreddits/part-00000: map of authors to the total link karma we observed (sorted)

    logger:info("loading subreddit metadata...")
    local fp = gzip.open(metadatadir.."/subreddits/part-00000.gz")
    local idx = 0
    local subreddits = {}
    for line in fp:lines() do
        idx = idx + 1
        local split = line:split(",")
        subreddits[split[1]] = idx
    end
    local numSubreddits = idx

    fp:close()

    -- the metadata stores a one-hot representation of it's rank
    for subreddit, idx in pairs(subreddits) do
        local onehot = torch.Tensor(numSubreddits):zero()
        onehot[idx] = 1.0
        subreddits[subreddit] = onehot
    end

    local authorMap = {}
    local idx = 0
    fp = gzip.open(metadatadir.."/authors/part-00000.gz")
    for line in fp:lines() do
        local split = line:split(",")
        authorMap[split[2]] = idx
        idx = idx+1
        if idx >= datautil.MAX_AUTHORS then
            break
        end
    end
    local numAuthors = idx
    fp:close()

    return {subreddits=subreddits, numSubreddits=numSubreddits, authors=authorMap, numAuthors=numAuthors}
end

-- provided a memoized version of loadmetadata
local loadMetadataCached = _.cache(loadMetadata)

function datautil.loadBatch(filepath, cachedir, metadatadir)
    local storyList = {}

    local metadata = loadMetadataCached(metadatadir)

    -- lets check if the batch was already cached
    local filename = filepath:match("([^/]+)$")
    local cachefilename = cachedir.."/"..filename
    local cachefp = io.open(cachefilename)
    if cachefp ~= nil then
        cachefp:close()

        --we can load from the cache
        logger:info("loading batch from cache located at '%s'", cachefilename)
        storyList = torch.load(cachefilename)
    else
        --now try to load the batch
        local fp = gzip.open(filepath, "r") --batch files are gziped
        if fp == nil then
            -- if cannot open, just return empty table and log
            logger:error("Cannot read batch file '%s'!", path)
            return {}
        end

        --each batch has one story per line
        for line in fp:lines() do 
            local decoded = cjson.decode(line)
            local historyCount = #decoded["history"]["timestamp"]

            -- create an object for each story that contains the following keys:
            --  * history - the matrix of history-based data to our model
            --  * expected - the matrix of expected prediction values (outputs) of our model
            --  * metadata - a table of individual vectors to be fed in as static inputs to the model

            local story = {}
            story.id = decoded["summary"]["id"]
            story.history = torch.Tensor(historyCount, 3)
            story.expected = torch.Tensor(historyCount, 1) 
            --create metadata input for network         
            local createdAt = decoded["summary"]["createdAt"]

            --moment of week is a scale from 0->1 where 0 is midnight on sunday, and 1 is 1 second before midnight on sunday
            local createdAtDate = os.date("*t", createdAt/1000.0)
            local secondOfDay = createdAtDate.hour*(60*60) + createdAtDate.min*(60) + createdAtDate.sec
            local metadataTimeOfWeek = torch.Tensor{((createdAtDate.wday-1)*(60*60*24) + secondOfDay)/(60*60*24*7)}

            -- get the onehot of the subreddit the story is in
            local metadataSubredditOneHot = metadata.subreddits[decoded["summary"]["subreddit"]]

            -- determine if the author ranks in one of the AUTHOR RANKS buckets
            local authorRanks = torch.Tensor(#datautil.AUTHOR_RANKS):zero()
            local thisAuthorRank = metadata.authors[decoded["summary"]["author"]]
            if thisAuthorRank ~= nil then
                for idx, rank in pairs(datautil.AUTHOR_RANKS) do
                    if thisAuthorRank < rank then
                        authorRanks[idx] = 1.0
                    end
                end
            end

            -- now some story flags: nsfw, isself, hasthumbnail
            local storyFlags = torch.Tensor(3):zero()

            -- is nsfw?
            if decoded["summary"]["isOver18"] == true then
                storyFlags[1] = 1.0
            end

            -- has thumbnail?
            local thumbnail = decoded["summary"]["thumbnail"]
            if type(thumbnail) == "string" and thumbnail ~= "self" and thumbnail ~= "default" and thumbnail ~= "nsfw" then
                storyFlags[2] = 1.0
            end

            -- is self?
            local isSelf = decoded["summary"]["isSelf"]
            if isSelf then
                storyFlags[3] = 1.0
            end

            -- finalize the metadata into a single table of vectors
            story.metadata = {metadataTimeOfWeek, metadataSubredditOneHot, authorRanks, storyFlags}

            -- now go through the history to generate the "expected" matrix
            local lastScore = 0
            local lastComments = 0
            for x = 1, historyCount do
                local timestamp = decoded["history"]["timestamp"][x]
                local score = decoded["history"]["score"][x]
                local comments = decoded["history"]["comments"][x]

                --0->1 where 1 is 48 hours after creation (our max retention)
                story.history[x][1] = (timestamp - createdAt)/datautil.MAX_STORY_RETENTION_MS
                
                --score/comments
                story.history[x][2] = score * datautil.SCORE_SCALE
                story.history[x][3] = comments * datautil.COMMENT_SCALE

                lastScore = score
                lastComments = comments

                local nextExpected = historyLerp(decoded["history"], x, 60*datautil.MINUTES_PREDICTED) 
                if nextExpected == nil then
                    -- we can't predict any further, so we should trim the history 
                    story.history:resize(x-1, 3)
                    story.expected:resize(x-1, 1)
                    story.size = x-1; -- store the size so it can be easily consumed
                    break
                end

                story.expected[x][1] = (nextExpected.score + score) * datautil.SCORE_SCALE
                --story.expected[x][2] = (nextExpected.comments + comments) * COMMENT_SCALE --ignore comment output for now
            end

            table.insert(storyList, story)
        end

        logger:info("caching %s to %s", filename, cachefilename)
        lfs.mkdir(cachedir) -- make the cache directory if it doesnt exist yet
        torch.save(cachefilename, storyList)
    end

    collectgarbage()
    return _.shuffle(storyList) -- shuffle the result to keep things random when minibatching
end

-- copys the batch to a table of gpuids and returns a map of the gpuid -> gpu reference
function datautil.copyStoryToGpu(story)
    local storyCopy = _.clone(story, true) -- make a deep clone of story
    storyCopy.history = storyCopy.history:cuda()
    storyCopy.expected = storyCopy.expected:cuda()
    for idx, metadataItem in pairs(storyCopy.metadata) do
        storyCopy.metadata[idx] = metadataItem:cuda()
    end
    return storyCopy
end

-- get a file listing given a directory - returns a list of strings
-- representing the absolute path of each file 
-- note: excludes empty files
function datautil.getFileListing(directory) 
    local listing = {}
    -- filter listing to get rid of nonfiles and empty files
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
