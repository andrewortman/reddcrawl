"Reddcrawl" has been my personal project for a few years. I use it as an excuse to learn a new language. This version is written in Java/Spring but I've also made version in Python, and Node.JS.

The goal of the project is to "predict the hivemind." There are several major components/stages of this project:

1. Collect as much historical data about every storing reaching the front page from the moment of it's conception to whenever it drops off of reddit's front page. This includes changes to it's score, hotness, number of comments, and reddit gold counts.
2. As this data is curated, archive older stories into a publically accessible repository
3. Use the archived data to generate machine learning models that can create a prediction curve and estimate the popularity of a story in it's infancy.
4. Make a easy to use web interface to explore the data and predictions
