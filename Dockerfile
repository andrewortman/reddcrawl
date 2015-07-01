FROM ubuntu:trusty

#java
RUN apt-get update && apt-get install -y software-properties-common && add-apt-repository ppa:webupd8team/java
RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN apt-get update && apt-get install -y oracle-java7-installer

#gradle
RUN add-apt-repository ppa:cwchien/gradle
RUN apt-get update
RUN apt-get install -y gradle

#Create user for our app
RUN adduser --disabled-password --gecos "" --home /home/reddcrawl --shell /bin/bash reddcrawl

#Add the run scripts and other container-related things like newrelic and log4j config
ADD docker/* /home/reddcrawl/

#Add the project files
ADD src /home/reddcrawl/tmp/src
ADD settings.gradle /home/reddcrawl/tmp/settings.gradle
ADD build.gradle /home/reddcrawl/tmp/build.gradle
RUN chown -R reddcrawl:reddcrawl /home/reddcrawl

WORKDIR /home/reddcrawl
RUN cd /home/reddcrawl/tmp && gradle --version \
    && gradle --info --console=plain --stacktrace build \
    && mv build/libs/*.jar ../ \
    && cd .. \
    && rm -rf tmp

USER reddcrawl
EXPOSE 8085
ENTRYPOINT ["/bin/bash", "run.sh"]
