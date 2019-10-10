FROM busybox:latest

RUN addgroup somegroup
RUN adduser -G somegroup someuser
USER someuser
ADD ./someFile.txt /tmp/

CMD ["cat"]