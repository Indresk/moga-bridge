/// Remembers the last value and reports whether a new one differs from it.
///
/// The controller repeats identical reports (idle heartbeats, unchanged polls); passing only
/// the changes on saves JNI calls, IPC events and UI renders.
pub struct ChangeFilter<T> {
    last: Option<T>,
}

impl<T: PartialEq + Clone> ChangeFilter<T> {
    pub fn new() -> Self {
        Self { last: None }
    }

    /// True when `value` is the first one or differs from the previous one.
    pub fn changed(&mut self, value: &T) -> bool {
        if self.last.as_ref() == Some(value) {
            return false;
        }
        self.last = Some(value.clone());
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn passes_only_changes() {
        let mut filter = ChangeFilter::new();
        assert!(filter.changed(&1));
        assert!(!filter.changed(&1));
        assert!(filter.changed(&2));
        assert!(filter.changed(&1));
    }
}
