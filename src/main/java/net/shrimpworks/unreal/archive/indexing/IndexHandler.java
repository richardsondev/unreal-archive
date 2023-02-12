package net.shrimpworks.unreal.archive.indexing;

import java.util.Collections;
import java.util.function.Consumer;

import net.shrimpworks.unreal.archive.content.Content;

public interface IndexHandler<T extends Content> {

	public interface IndexHandlerFactory<T extends Content> {

		public IndexHandler<T> get();
	}

	public void index(Incoming incoming, Content current, Consumer<IndexResult<T>> completed);

	public static class NoOpIndexHandlerFactory implements IndexHandlerFactory<Content> {

		@Override
		public IndexHandler<Content> get() {
			return (incoming, current, completed) -> completed.accept(new IndexResult<>(current, Collections.emptySet()));
		}
	}
}
