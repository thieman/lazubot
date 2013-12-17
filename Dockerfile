FROM thieman/clojure
MAINTAINER Travis Thieman, travis.thieman@gmail.com

RUN git clone https://github.com/tthieman/lazubot.git
WORKDIR /lazubot

# running this as root clears up permission issues
# when sandboxer runs lazubot later
RUN cd /lazubot; lein deps

# sandbox user and directory configuration
ENV lazubot_sandbox_dir /sandbox
RUN useradd sandboxer
RUN passwd -d -u sandboxer
RUN mkdir /home/sandboxer
RUN chown -R sandboxer /home/sandboxer
RUN mkdir /sandbox
RUN chown -R sandboxer /sandbox

# anything in this container is run as sandboxer
USER sandboxer

# copy over private resources and java policy
ADD resources/private /lazubot/resources/private
ADD resources/public/java.policy /home/sandboxer/.java.policy

# run lazubot if no other arguments given to docker run
CMD lein run
