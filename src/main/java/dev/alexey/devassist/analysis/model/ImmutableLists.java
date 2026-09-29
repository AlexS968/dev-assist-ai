package dev.alexey.devassist.analysis.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ImmutableLists {
	private ImmutableLists() {
	}

	// Preserve null lists/elements so the validator can report every invalid field safely.
	static <T> List<T> copy(List<T> source) {
		return source == null ? null : Collections.unmodifiableList(new ArrayList<>(source));
	}
}
