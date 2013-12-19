# This is the Dockerfile for the worker

FROM thieman/clojure
MAINTAINER Travis Thieman, travis.thieman@gmail.com

RUN git clone https://github.com/tthieman/lazubot.git
WORKDIR /lazubot

# ZMQ
RUN add-apt-repository -y ppa:chris-lea/zeromq
RUN apt-get update -qq
RUN apt-get install -qq -y libzmq3-dbg libzmq3-dev libzmq3

# set up sandboxer user so iptables are secure
RUN useradd sandboxer && passwd -d -u sandboxer
RUN mkdir /home/sandboxer
RUN chown -R sandboxer /home/sandboxer
ADD java.policy /home/sandboxer/.java.policy
RUN chown -R sandboxer /lazubot
RUN cd /lazubot
RUN sudo -u sandboxer lein deps

# firewall rules to limit network access to the zeromq port only
RUN apt-get install -qq -y iptables-persistent aufs-tools
RUN iptables -A INPUT -p tcp --dport 8080 -j ACCEPT &&\
	iptables -A OUTPUT -p tcp --sport 8080 -j ACCEPT &&\
	iptables -P INPUT DROP &&\
	iptables -P FORWARD DROP &&\
	iptables -P OUTPUT DROP &&\
	iptables-save > /etc/iptables/rules.v4
RUN service iptables-persistent start

USER sandboxer

# run lazubot if no other arguments given to docker run
CMD lein with-profile worker run
