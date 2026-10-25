package ch.moldexj.view.finder;

import ch.moldexj.view.View;

import java.util.Collection;

/** A source of views with parameters. Called inside a read action. */
public interface ViewFinder {
    Collection<View> findViews();
}
