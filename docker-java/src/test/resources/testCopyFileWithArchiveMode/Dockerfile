FROM alpine:latest

RUN addgroup somegroup
RUN adduser -G somegroup -D someuser
USER someuser

ADD ./someFile.txt /tmp/