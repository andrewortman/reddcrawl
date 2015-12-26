import pyspark
import json
import datetime
import random
import math
import hashlib
import operator

TEST_SIZE = 0.0005 # the total % of stories in our combined (validation+test) set
SHARD_SIZE = 256 # after loading the large number of small archive files, out many shards to partition them by?
TRAIN_BATCHES = 128 # number of batches to generate for train set
DAYS_TO_KEEP = 58 #56 + 2 days (8 weeks + 2 days to account for archival delay)

sc = pyspark.SparkContext();

# decode a single story json into a map
def decodeStory(str):
        decoded = json.loads(str)
        return decoded

# save back to a compressed json
def toJson(data):
        hashd, story = data
        return json.dumps(story, separators=(',',':'))

# filters a story out if it is too old (before DAYS_TO_KEEP)
def filterOld(data):   
        hashd, story = data
        createdDate = datetime.date.fromtimestamp(story["summary"]["createdAt"]/1000)
        return ((datetime.date.today() - createdDate).days < DAYS_TO_KEEP)

# we dont want to keep stories that were stored and have some issues, like:
# 1) score too small over time (we wont even start to predict stories until they reach a threshold anyways)
# 2) if we discovered it far too late (> 10 minutes after create date)
# 3) if the history has a hold > 10 minutes within the first 3 hours in it
def filterLowQuality(data):   
        hashd, story = data
        #if too small of a score
        if story["summary"]["score"] < 20 :
                return False

        # if discovered way after it was created
        discoveredAt = story["summary"]["discoveredAt"]
        createdAt = story["summary"]["createdAt"]
        if (discoveredAt - createdAt) > (1000*60*10): #more than 10 minutes
                return False

        if (discoveredAt < createdAt):
                return False # just weirdness


        if (max(story["history"]["timestamp"]) - createdAt) < (1000*60*60*24):
                return False # not enough history (<24 hours tracked)

        # if any timestamps are more than 10 minutes apart in the first 3 hours
        lastTimestamp = 0
        for timestamp in story["history"]["timestamp"]:
                if lastTimestamp == 0:
                        lastTimestamp = timestamp
                        continue

                #bail early if we passed 3 hours of time
                sinceCreate = timestamp - createdAt
                if sinceCreate > (1000*60*60*3):
                        break;

                difference = timestamp - lastTimestamp
                lastTimestamp = timestamp

                if difference > (1000*60*10):
                        return False
        return True

def cleanupHistory(data):
        hashd, story = data
        timesteps = len(story["history"]["timestamp"])
        for i in range(0, timesteps):
                if story["history"]["score"][i] < 0:
                        story["history"]["score"][i] = 0

                if story["history"]["comments"][i] < 0:
                        story["history"]["comments"][i] = 0
        
        return (hashd, story)

def extractMaxScoreForAuthor(data):
        hashd, story = data
        return (story["summary"]["author"], story["meta"]["max_score"])

def extractMaxScoreForSubreddit(data):
        hashd, story = data
        return (story["summary"]["subreddit"], story["meta"]["max_score"])

def extractMaxScoreForDomain(data):
        hashd, story = data
        if story["summary"]["isSelf"]:
                # ignore self domains
                return ("self.domain", 0)

        return (story["summary"]["domain"], story["meta"]["max_score"])

def preprocessStory(story):
        hashd = int(str(hashlib.md5(story["summary"]["id"]).hexdigest()), 32) #get the 32 byte hash
        story["meta"] = {}
        story["meta"]["hash"] = hashd

        story["meta"]["max_score"] = max(story["history"]["score"])
        story["meta"]["max_comments"] = max(story["history"]["comments"])
        story["meta"]["max_gilded"] = max(story["history"]["gilded"])

        if hashd < int('f'*32, 32)*TEST_SIZE:
                story["set"] = "test"
        else:
                story["set"] = "train"

        return (hashd, story)

allStories = sc.textFile("gs://reddcrawl/*/*.json.gz") \
        .map(decodeStory) \
        .map(preprocessStory) \
        .partitionBy(SHARD_SIZE, lambda _: random.randint(0, SHARD_SIZE-1)) \
        .cache()

#metadata generation (go against all stories here)
topAuthorsByMaxScore = allStories.map(extractMaxScoreForAuthor).reduceByKey(operator.add).sortBy(lambda x: -x[1]).coalesce(1).map(lambda x: str(x[1]) + "," + str(x[0])).saveAsTextFile("gs://reddcrawl-processed/meta/authors", "org.apache.hadoop.io.compress.GzipCodec")
topSubredditsByMaxScore = allStories.map(extractMaxScoreForSubreddit).reduceByKey(operator.add).sortBy(lambda x: -x[1]).coalesce(1).map(lambda x: str(x[0]) + "," + str(x[1])).saveAsTextFile("gs://reddcrawl-processed/meta/subreddits", "org.apache.hadoop.io.compress.GzipCodec")
topDomainsByMaxScore = allStories.map(extractMaxScoreForDomain).reduceByKey(operator.add).sortBy(lambda x: -x[1]).coalesce(1).map(lambda x: str(x[0]) + "," + str(x[1])).saveAsTextFile("gs://reddcrawl-processed/meta/domains", "org.apache.hadoop.io.compress.GzipCodec")

#now the filtering happens, we need to filter the stuff we really dont care about out and then split that up into test/train
filteredStories = allStories.filter(filterOld).filter(filterLowQuality).map(cleanupHistory).cache()
filteredStories.filter(lambda (_, s): s["set"] == "train").partitionBy(TRAIN_BATCHES, lambda _: random.randint(0, TRAIN_BATCHES-1)).map(toJson).saveAsTextFile("gs://reddcrawl-processed/train", "org.apache.hadoop.io.compress.GzipCodec")
filteredStories.filter(lambda (_, s): s["set"] == "test").coalesce(1).map(toJson).saveAsTextFile("gs://reddcrawl-processed/test", "org.apache.hadoop.io.compress.GzipCodec")

#dump all stories to much larger and consumable "shards" - we can process these directly with bigquery, for example
allStories.map(toJson).saveAsTextFile("gs://reddcrawl-all", "org.apache.hadoop.io.compress.GzipCodec")
