FROM busybox:latest

RUN groupadd -g 1000 somegroup
RUN useradd -m -u 1000 someuser
USER someuser
ADD ./someFile.txt /tmp/

CMD ["cat"]