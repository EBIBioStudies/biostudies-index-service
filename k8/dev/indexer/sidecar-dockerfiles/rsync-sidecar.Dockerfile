FROM alpine:3.19

RUN apk add --no-cache rsync bash

CMD ["bash", "-c", "tail -f /dev/null"]