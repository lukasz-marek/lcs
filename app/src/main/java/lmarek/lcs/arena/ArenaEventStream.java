package lmarek.lcs.arena;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Each subscriber owns one sender and one replaceable pending revision. No I/O under arena locks.
 */
final class ArenaEventStream {
  private static final long STREAM_TIMEOUT_MILLIS = 30 * 60 * 1_000L;
  private final Set<Subscriber> subscribers = new HashSet<>();
  private boolean closed;

  synchronized SseEmitter subscribe(ArenaUpdate current) {
    if (subscribers.size() >= 32) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "At most 32 subscribers per run");
    }
    var subscriber = new Subscriber();
    subscribers.add(subscriber);
    subscriber.emitter.onCompletion(subscriber::disconnect);
    subscriber.emitter.onTimeout(subscriber::disconnect);
    subscriber.emitter.onError(error -> subscriber.disconnect());
    subscriber.mailbox.offer(current);
    if (closed) subscriber.mailbox.finish();
    subscriber.sender.start();
    return subscriber.emitter;
  }

  synchronized void publish(ArenaUpdate update) {
    if (!closed) subscribers.forEach(subscriber -> subscriber.mailbox.offer(update));
  }

  synchronized void close() {
    closed = true;
    subscribers.forEach(subscriber -> subscriber.mailbox.finish());
  }

  /** Single pending item, monotonic revisions, and terminal drain are one atomic protocol. */
  static final class Mailbox {
    private @Nullable ArenaUpdate pending;
    private long highestRevision = -1;
    private boolean closed;

    synchronized void offer(ArenaUpdate update) {
      if (!closed && update.revision() > highestRevision) {
        highestRevision = update.revision();
        pending = update;
        notifyAll();
      }
    }

    synchronized @Nullable ArenaUpdate take() throws InterruptedException {
      while (pending == null && !closed) wait();
      var next = pending;
      pending = null;
      return next;
    }

    synchronized void finish() {
      closed = true;
      notifyAll();
    }

    synchronized void disconnect() {
      pending = null;
      closed = true;
      notifyAll();
    }
  }

  private final class Subscriber {
    private final SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
    private final Mailbox mailbox = new Mailbox();
    private final Thread sender = Thread.ofVirtual().name("arena-sse").unstarted(this::send);

    private void disconnect() {
      mailbox.disconnect();
      synchronized (ArenaEventStream.this) {
        subscribers.remove(this);
      }
      sender.interrupt();
    }

    private void send() {
      try {
        ArenaUpdate update;
        while ((update = mailbox.take()) != null) {
          emitter.send(
              SseEmitter.event().id(Long.toString(update.revision())).name("update").data(update));
        }
        emitter.complete();
      } catch (IOException | IllegalStateException exception) {
        emitter.completeWithError(exception);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        emitter.complete();
      } finally {
        disconnect();
      }
    }
  }
}
