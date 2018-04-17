package com.idm;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SIBLINGS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.EnumSet;

/**
 * Copies files from Photos to a file structure on the destination drive which
 * is based on the creation date of the file being copied
 */

public class PhotosCopy {

	private static class ConflictResolver {

		private Path sourcePath;
		private Path targetPath;
		private Path targetFile;
		private FileMetadata sourceFileMetadate;

		public ConflictResolver(Path source, Path target, FileMetadata sourceFmd) {

			sourcePath = source;
			targetPath = target;
			sourceFileMetadate = sourceFmd;

		}

		public boolean fileExists() throws IOException {

			targetFile = targetPath.resolve(sourcePath.getFileName());

			return this.resolveFileName(1);

		}

		private boolean resolveFileName(int count) throws IOException {

			// If the targetFile does note exist, just continue.
			if (!Files.exists(targetFile)) {
				return false;
			}

			// If the targetFile exists, check if the two files are the same.
			// Files.isSameFile(path, path2) should do this, but does not appear
			// to work.
			// Using the creation date is dodgy since not all file systems
			// appear to
			// respect it, specially on shared drives.
			// So check if the file has Exif tags, use this.
			// If the files does not have Exif tags, ignore the target file
			// creation time
			// and just compare on size.
			// If it is a different size, it is either a different
			// file, or a new version of the same file.

			LinkOption[] linkOptions = new LinkOption[] { LinkOption.NOFOLLOW_LINKS };

			BasicFileAttributes targetAttrs = Files.readAttributes(targetFile, BasicFileAttributes.class, linkOptions);
			FileMetadata tmd = new FileMetadata(targetFile, targetAttrs);

			if (tmd.hasDatetime_original()) {
				if ((tmd.fileSize() == sourceFileMetadate.fileSize())
						&& tmd.datetime_original().equals(sourceFileMetadate.datetime_original())) {
					// It is the same file, continue.
					return true;
				} else {
					this.createNewTargetFilename(count);
					return this.resolveFileName(++count);
				}
			} else {
				// No there is not datetime_original Exif Tag, ignore the creationTime for comparison
				if (tmd.fileSize() == sourceFileMetadate.fileSize()) {
					// It is the same file, just continue.
					return true;
				} else {
					// The files are not the same, but they have the same name
					// Create a new filename by appending a counter to the end
					// and try again.

					this.createNewTargetFilename(count);
					return this.resolveFileName(++count);
				}

			}

		}

		private void createNewTargetFilename(int count) {
			String fullFileName = sourcePath.getFileName().toString();
			int i = fullFileName.lastIndexOf(".");
			String fileName = fullFileName.substring(0, i);
			String extension = fullFileName.substring(i, fullFileName.length());

			StringBuilder builder = new StringBuilder();
			builder.append(fileName);
			builder.append("_");
			builder.append(count);
			builder.append(extension);
			targetFile = targetPath.resolve(builder.toString());
		}

		public Path targetFile() {
			// This method can only be called AFTER fileExists() has been
			// called.
			return targetFile;
		}

	}

	private String source;
	private String dest;
	private Path sourcePath;
	private Path destPath;

	/**
	 * A {@code FileVisitor} that copies a file-tree
	 */
	class TreeCopier implements FileVisitor<Path> {
		private final Path source;
		private final Path target;
		private int filesVisitedCount = 0;
		private int filesCopiedCount = 0;

		TreeCopier(Path source, Path target) {
			this.source = source;
			this.target = target;
		}

		private void copyFile(Path source, Path target, FileMetadata fmd) {

			ConflictResolver cr = new ConflictResolver(source, target, fmd);

			try {

				if (!cr.fileExists()) {

					Files.createDirectories(target, new FileAttribute<?>[0]);
//					PhotosCopy.this.log("Copying file: %s%n", cr.targetFile());
					CopyOption[] options = new CopyOption[] { COPY_ATTRIBUTES };
					Files.copy(source, cr.targetFile(), options);
					filesCopiedCount++;
				}
			} catch (IOException x) {
				PhotosCopy.this.logError("Unable to copy: %s: %s%n", source, x);
			}
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
			// Do nothing
			
			try {
				if (attrs.isSymbolicLink() || Files.isHidden(dir)) {
					return SKIP_SIBLINGS;
				}
			} catch (IOException e) {
				PhotosCopy.this.logError("An Error Occured: %s: %s%n", dir, e);
			}
			
			return CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

			filesVisitedCount++;
//			PhotosCopy.this.log("Checking File: %s%n", file);
			
			if (attrs.size() > 0) {
				FileMetadata fmd = new FileMetadata(file, attrs);

				//Skip if hidden file
				if (!fmd.isHidden()) {
					String format = fmd.targetFilePath();
					this.copyFile(file, target.resolve(format), fmd);
				}
			}
			return CONTINUE;

		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
			// Do nothing
			return CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) {
			if (exc instanceof FileSystemLoopException) {
				PhotosCopy.this.logError("cycle detected: %s%n", file);
			} else {
				PhotosCopy.this.logError("Unable to copy: %s: %s%n", file, exc);
			}
			return CONTINUE;
		}

		public int filesVisitedCount() {
			return filesVisitedCount;
		}

		public Object filesCopiedCount() {
			return filesCopiedCount;
		}
	}

	private void usage() {
		this.logError("java PhotosCopy source... target");
		System.exit(-1);
	}

	public static void main(String[] args) throws IOException {

		new PhotosCopy().copy(args);

	}

	private void copy(String[] args) {

		if (args.length != 2)
			usage();

		this.log("Starting sync%n");
		long startTime = System.currentTimeMillis();
		source = args[0];
		dest = args[1];

		this.log("Syncing Photos from Library: %s to: %s%n", source, dest);

		sourcePath = Paths.get(source);
		destPath = Paths.get(dest);

		if (!Files.isDirectory(sourcePath)) {
			this.log("The source parameter is not a directory");
			return;
		}

		if (!Files.isDirectory(destPath)) {
			this.log("The destination parameter is not a directory");
			return;
		}

		// follow links when copying files
		EnumSet<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		TreeCopier tc = new TreeCopier(sourcePath, destPath);
		try {
			Files.walkFileTree(sourcePath, opts, Integer.MAX_VALUE, tc);
		} catch (IOException e) {
			this.logError("An error occured %n", e);
		}

		long timeTaken = System.currentTimeMillis() - startTime;
		this.log("%d files checked.%n", tc.filesVisitedCount());
		this.log("%d files copied.%n", tc.filesCopiedCount());
		this.log("Finished sync in: %dms %n", timeTaken);

	}

	private void log(String message, Object... args) {

		System.out.format(message, args);

	}

	private void logError(String message, Object... args) {

		System.err.format(message, args);

	}

}