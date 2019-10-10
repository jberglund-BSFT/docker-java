FROM busybox:latest

RUN ['/bin/busybox','addgroup','somegroup']
RUN ['/bin/busybox','adduser','-G','somegroup','someuser']
USER someuser

ADD ./someFile.txt /tmp/