use std::time::{Duration, Instant};

/// Lets an action through at most once per `interval` (used to avoid flooding logs and IPC).
pub struct Throttle {
    interval: Duration,
    last: Option<Instant>,
}

impl Throttle {
    pub fn new(interval: Duration) -> Self {
        Self {
            interval,
            last: None,
        }
    }

    /// True if the action may run now; records the time when it does.
    pub fn ready(&mut self) -> bool {
        let now = Instant::now();
        match self.last {
            Some(last) if now.duration_since(last) < self.interval => false,
            _ => {
                self.last = Some(now);
                true
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn allows_the_first_call_then_blocks_within_the_interval() {
        let mut throttle = Throttle::new(Duration::from_secs(3600));
        assert!(throttle.ready());
        assert!(!throttle.ready());
    }

    #[test]
    fn a_zero_interval_never_blocks() {
        let mut throttle = Throttle::new(Duration::ZERO);
        assert!(throttle.ready());
        assert!(throttle.ready());
    }
}
