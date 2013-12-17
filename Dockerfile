FROM thieman/clojure
MAINTAINER Travis Thieman, travis.thieman@gmail.com

RUN git clone https://github.com/tthieman/lazubot.git
WORKDIR /lazubot
ENV LEIN_ROOT 1
RUN cd /lazubot; lein deps

# workaround since Docker container overwrites initctl
RUN dpkg-divert --local --rename --add /sbin/initctl
RUN ln -s /bin/true /sbin/initctl

# Docker in Docker support
RUN apt-get update -qq
RUN apt-get install -qqy iptables ca-certificates lxc libzmq-dev
ADD /lazubot/resources/public/docker /usr/local/bin/docker
ADD /lazubot/resources/public/wrapdocker /usr/local/bin/wrapdocker
RUN chmod +x /usr/local/bin/docker /usr/local/bin/wrapdocker
VOLUME /var/lib/docker

# copy over private config
ADD resources/private /lazubot/resources/private

# clients link to the master using Docker's linking facility
EXPOSE 8080

# run lazubot if no other arguments given to docker run
CMD lein with-profile master run
