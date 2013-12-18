# This is the Dockerfile for the worker

FROM thieman/clojure
MAINTAINER Travis Thieman, travis.thieman@gmail.com

RUN git clone -b split https://github.com/tthieman/lazubot.git
WORKDIR /lazubot
ENV LEIN_ROOT 1
RUN cd /lazubot; lein deps

# ZMQ
RUN add-apt-repository -y ppa:chris-lea/zeromq
RUN apt-get update -qq
RUN apt-get install -qq -y libzmq3-dbg libzmq3-dev libzmq3

# firewall rules to limit network access to the zeromq port only
RUN apt-get install -qq -y iptables aufs-tools
RUN iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
RUN iptables -A OUTPUT -p tcp --sport 8080 -j ACCEPT

# set up sandboxer user so iptables are secure
RUN useradd sandboxer
RUN passwd -d -u sandboxer
RUN mkdir /home/sandboxer
RUN chown -R sandboxer /home/sandboxer
ADD resources/public/java.policy /home/sandboxer/.java.policy

# run lazubot if no other arguments given to docker run
CMD lein with-profile worker run