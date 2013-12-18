FROM thieman/clojure
MAINTAINER Travis Thieman, travis.thieman@gmail.com

RUN git clone -b split https://github.com/tthieman/lazubot.git
WORKDIR /lazubot
ENV LEIN_ROOT 1
RUN cd /lazubot; lein deps

# workaround since Docker container overwrites initctl
RUN dpkg-divert --local --rename --add /sbin/initctl
RUN ln -s /bin/true /sbin/initctl

# ZMQ
RUN add-apt-repository -y ppa:chris-lea/zeromq
RUN apt-get update -qq
RUN apt-get install -qq -y libzmq3-dbg libzmq3-dev libzmq3

# Docker in Docker support
RUN apt-get install -qq -y aufs-tools iptables ca-certificates lxc
ADD resources/public/docker /usr/local/bin/docker
ADD resources/public/wrapdocker /usr/local/bin/wrapdocker
RUN chmod +x /usr/local/bin/docker /usr/local/bin/wrapdocker
VOLUME /var/lib/docker

# child container rootfs app armor workaround
ADD resources/public/lxc-default /etc/apparmor.d/lxc/lxc-default

# copy over private config
ADD resources/private /lazubot/resources/private

# clients link to the master using Docker's linking facility
EXPOSE 8080

# run lazubot if no other arguments given to docker run
CMD wrapdocker
