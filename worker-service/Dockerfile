FROM eclipse-temurin:21-jdk AS stockfish-builder

ARG STOCKFISH_SOURCE_URL
ARG STOCKFISH_SOURCE_SHA256

RUN test -n "$STOCKFISH_SOURCE_URL" \
    && test -n "$STOCKFISH_SOURCE_SHA256" \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp
RUN curl --fail --silent --show-error --location "$STOCKFISH_SOURCE_URL" -o stockfish.tar.gz \
    && echo "$STOCKFISH_SOURCE_SHA256  stockfish.tar.gz" | sha256sum --check --strict \
    && mkdir source \
    && tar --extract --gzip --file stockfish.tar.gz --strip-components=1 --directory source

WORKDIR /tmp/source/src
RUN case "$(dpkg --print-architecture)" in \
      amd64) make -j"$(nproc)" build ARCH=x86-64 ;; \
      arm64) make -j"$(nproc)" build ARCH=armv8 ;; \
      *) echo "Unsupported architecture: $(dpkg --print-architecture)" >&2; exit 1 ;; \
    esac \
    && mkdir -p /opt/stockfish /opt/stockfish-source \
    && cp stockfish /opt/stockfish/stockfish \
    && cp /tmp/stockfish.tar.gz /opt/stockfish-source/stockfish-source.tar.gz \
    && cp /tmp/source/Copying.txt /opt/stockfish-source/Copying.txt \
    && chmod 0755 /opt/stockfish/stockfish

FROM eclipse-temurin:21-jdk AS app-builder

WORKDIR /workspace
COPY . .
RUN ./gradlew :worker-service:installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre

ENV STOCKFISH_PATH=/opt/stockfish/stockfish
ENV APP_ENV=prod

RUN useradd --system --create-home --uid 10001 chesscoach
WORKDIR /app
COPY --from=app-builder /workspace/worker-service/build/install/worker-service/ /app/
COPY --from=stockfish-builder /opt/stockfish/ /opt/stockfish/
COPY --from=stockfish-builder /opt/stockfish-source/ /usr/share/doc/stockfish/

USER chesscoach
ENTRYPOINT ["/app/bin/worker-service"]
