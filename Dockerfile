FROM thieman/clojure
MAINTAINER Travis Thieman, travis.thieman@gmail.com

RUN apt-get update
RUN apt-get install libzmq-dev -y

RUN git clone https://github.com/tthieman/lazubot.git
WORKDIR /lazubot
RUN cd /lazubot; lein deps

# copy over private config
ADD resources/private /lazubot/resources/private

# clients link to the master using Docker's linking facility
EXPOSE 8080

# run lazubot if no other arguments given to docker run
CMD lein with-profile master run
