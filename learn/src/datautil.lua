local cjson = require "cjson"
local lfs = require "lfs"
local logger = require "logger"
local gzip = require "gzip"
local _ = require "moses"
local dbg = require "lib/debugger"

local datautil = {}

datautil.SCORE_SCALE = (1/1000)

datautil.COMMENT_SCALE = (1/1000)
datautil.MAX_AUTHORS = 1000 -- max authors to consider when ranking
datautil.MAX_DOMAINS = 1000 -- max domains to consider when ranking
datautil.AUTHOR_RANKS = {5, 10, 50, 100, 500, 1000}
datautil.DOMAIN_RANKS = {5, 10, 50, 100, 500, 1000}
datautil.MAX_STORY_RETENTION_MS = (48*60*60*1000.0)

-- this is the time between samples in ms we are feeding into the network (ie, each sample is an interval after the previous sample)
-- each story has a nonuniform sample rate, so we convert it into a uniformly sampled story rate by using linear interpolation
datautil.RESAMPLE_INTERVAL = (60*5*1000)

-- resamples using linear interpolation the history of a story
-- requires you to pass in the created timestamp so we can properly interpolate the beginning
local function resampleHistory(history, createdAt)
  -- should return the history reinterpolated in finite steps that we can use efficiently

  local timestamps = history["timestamp"]
  local scores = history["score"]
  local comments = history["comments"]

  local firstTimestamp = timestamps[1]
  local lastTimestamp = timestamps[#timestamps]
  local numSamples = math.floor((lastTimestamp - firstTimestamp) / datautil.RESAMPLE_INTERVAL)

  local output = {}
  output.size = numSamples+1
  output.timestamp = torch.Tensor(output.size):zero()
  output.score = torch.Tensor(output.size):zero()
  output.comments = torch.Tensor(output.size):zero()


  local last = {}
  last.timestamp = createdAt
  last.score = 1
  last.comments = 0
  last.index = 0

  -- initial state
  output.timestamp[1] = 0
  output.score[1] = 1
  output.comments[1] = 0

  for sample = 1, numSamples do
    -- find the next point that is after the sample
    -- note: we shouldn't overrun and we should always be guaranteed some sample 
    -- because we trimmed off when calculating numSamples
    local targetTimestamp = (sample * datautil.RESAMPLE_INTERVAL) -- offset from createdAt (in milliseconds)
    for x = last.index+1, #timestamps do
      local new = {}
      new.timestamp = (timestamps[x] - createdAt) -- offset from createdAt in milliseconds
      new.score = scores[x]
      new.comments = comments[x]
      new.index = x

      if new.timestamp < targetTimestamp then
        last = new
      else 
        local timeDelta = (new.timestamp - last.timestamp)
        local scoreSlope = (new.score - last.score) / timeDelta
        local commentSlope = (new.comments - last.comments) / timeDelta 

        output.timestamp[sample+1] = targetTimestamp
        output.score[sample+1] = (scoreSlope * (targetTimestamp - last.timestamp)) + last.score
        output.comments[sample+1] = (commentSlope * (targetTimestamp - last.timestamp)) + last.comments
        break
      end
    end
  end

  return output
end

-- utility function that will load the metadata from disk
local function loadMetadata(metadatadir) 
  --load metadata from disk   --the metadatadir contains two folders produced by our spark job:
  -- {metadatadir}/authors/part-00000: map of authors to the total link karma we saw from them (sorted)
  -- {metadatadir}/domains/part-00000: map of domains to the total link karma we saw from them (sorted)
  -- {metadatadir}/subreddits/part-00000: map of subreddits to the total link karma we observed (sorted)

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

  -- the subreddit metadata stores a one-hot representation of it's rank
  for subreddit, idx in pairs(subreddits) do
    local onehot = torch.Tensor(numSubreddits):zero()
    onehot[idx] = 1.0
    subreddits[subreddit] = onehot
  end

  local domainMap = {}
  local idx = 0
  fp = gzip.open(metadatadir.."/domains/part-00000.gz")
  for line in fp:lines() do
    local split = line:split(",")
    domainMap[split[2]] = idx
    idx = idx+1
    if idx >= datautil.MAX_AUTHORS then
      break
    end
  end

  local numDomains = idx
  fp:close()


  local authorMap = {}
  local idx = 0
  fp = gzip.open(metadatadir.."/authors/part-00000.gz")
  for line in fp:lines() do
    local split = line:split(",")
    authorMap[split[2]] = idx
    idx = idx+1
    if idx >= datautil.MAX_DOMAINS then
      break
    end
  end

  local numAuthors = idx
  fp:close()

  return {subreddits=subreddits, numSubreddits=numSubreddits, domains=domainMap, numDomains=domains, authors=authorMap, numAuthors=numAuthors}
end

-- provided a memoized version of loadmetadata
local loadMetadataCached = _.cache(loadMetadata)

function datautil.loadBatch(filepath, cachedir, metadatadir, shuffle)
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
    logger:info("could not find batch in cache - going to preprocess. this may take a minute or two")
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

      local story = {}
      story.id = decoded["summary"]["id"]

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

      -- determine if the domain ranks in one of the DOMAIN RANKS buckets
      local domainRanks = torch.Tensor(#datautil.DOMAIN_RANKS):zero()
      local thisDomainRank = metadata.domains[decoded["summary"]["domain"]]
      if thisDomainRank ~= nil then
        for idx, rank in pairs(datautil.DOMAIN_RANKS) do
          if thisDomainRank < rank then
            domainRanks[idx] = 1.0
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
      story.metadata = {metadataTimeOfWeek, metadataSubredditOneHot, authorRanks, domainRanks, storyFlags}
      
      -- now lets build out the history - first we need to resample the history into discrete steps
      local resampledHistory = resampleHistory(decoded["history"], decoded["summary"]["createdAt"])

      story.size = resampledHistory.size - 1

      story.expected = torch.Tensor(story.size, 1)
      story.history = torch.Tensor(story.size, 3)
      for x = 1, story.size do 
        local timestamp = resampledHistory.timestamp[x]
        local score = resampledHistory.score[x]
        local comments = resampledHistory.comments[x]

        story.history[x][1] = timestamp/datautil.MAX_STORY_RETENTION_MS
        story.history[x][2] = score * datautil.SCORE_SCALE
        story.history[x][3] = comments * datautil.COMMENT_SCALE

        story.expected[x] = resampledHistory["score"][x+1] * datautil.SCORE_SCALE
      end
      table.insert(storyList, story)
    end

    logger:info("caching %s to %s", filename, cachefilename)
    lfs.mkdir(cachedir) -- make the cache directory if it doesnt exist yet
    torch.save(cachefilename, storyList)
  end

  if(shuffle) then
    storyList = _.shuffle(storyList) -- shuffle the result to keep things random when minibatching
  end

  collectgarbage()
  return storyList
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
