package com.idm;

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

/**
 * Copies files from Photos to a file structure on the destination drive which
 * is based on the creation date of the file being copied
 */

public class PhotosCheck {

	final SimpleDateFormat formatter = new SimpleDateFormat(
			"YYYY" + File.separatorChar + "MM" + File.separatorChar + "dd");

	private String source;
	private Path sourcePath;

	/**
	 * A {@code FileVisitor} that copies a file-tree
	 */
	class TreeCopier implements FileVisitor<Path> {
		private int filesVisitedCount = 0;
		private int zeroSizeFilesCount = 0;
		private int noExifDateFile = 0;
		private int nonImageFile = 0;

		TreeCopier() {
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
			// Do nothing
			return CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {

			filesVisitedCount++;

			LinkOption[] linkOptions = new LinkOption[] { LinkOption.NOFOLLOW_LINKS };

			BasicFileAttributes sourceAttrs;
			try {
				sourceAttrs = Files.readAttributes(path, BasicFileAttributes.class, linkOptions);
				if (sourceAttrs.size() == 0l) {
					zeroSizeFilesCount++;
					PhotosCheck.this.log("Zero size file: %s%n", path);
					// Nothing more to do
					return CONTINUE;
				}
			} catch (IOException e) {
				PhotosCheck.this.logError("Error reading file attributes: %s - %s%n", path.toString(), e);
			}

			// Exif attribute checking
			try {
				File file = new File(path.toString());
				Metadata metadata = ImageMetadataReader.readMetadata(file);
				Directory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
				if (directory == null) {
					noExifDateFile++;
					PhotosCheck.this.logError("No Exif date tag: %s%n", path.toString());
				} else {
					Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
					if (date == null) {
						noExifDateFile++;
						PhotosCheck.this.logError("No Exif date tag: %s%n", path.toString());
					}
				}
			} catch (ImageProcessingException|IOException e) {
				nonImageFile++;
				PhotosCheck.this.logError("Error parsing file: %s - %s%n", path.toString(), e);
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
				PhotosCheck.this.logError("Cycle detected: %s%n", file);
			} else {
				PhotosCheck.this.logError("Unknown error: %s: %s%n", file, exc);
			}
			return CONTINUE;
		}

		public int filesVisitedCount() {
			return filesVisitedCount;
		}

		public int zeroSizeFilesCount() {
			return zeroSizeFilesCount;
		}

		public int noExifDateFile() {
			return noExifDateFile;
		}

		public int nonImageFile() {
			return nonImageFile;
		}

	}

	private void usage() {
		this.logError("java PhotosCheck <source>");
		System.exit(-1);
	}

	public static void main(String[] args) throws IOException {

		new PhotosCheck().copy(args);

	}

	private void copy(String[] args) {

		if (args.length != 1)
			usage();

		this.log("Starting Check%n");
		long startTime = System.currentTimeMillis();
		source = args[0];

		this.log("Checking Photos from Library: %s%n", source);

		sourcePath = Paths.get(source);

		if (!Files.isDirectory(sourcePath)) {
			this.log("The source parameter is not a directory");
			return;
		}

		// follow links when copying files
		EnumSet<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		TreeCopier tc = new TreeCopier();
		try {
			Files.walkFileTree(sourcePath, opts, Integer.MAX_VALUE, tc);
		} catch (IOException e) {
			this.logError("An error occured - %s%n", e);
		}

		long timeTaken = System.currentTimeMillis() - startTime;
		this.log("%d files checked.%n", tc.filesVisitedCount());
		this.log("%d zero size files found.%n", tc.zeroSizeFilesCount());
		this.log("%d non-image files found.%n", tc.nonImageFile());
		this.log("%d image files found with no Exif date.%n", tc.noExifDateFile());
		this.log("Finished sync in: %dms %n", timeTaken);

	}

	private void log(String message, Object... args) {

		System.out.format(message, args);

	}

	private void logError(String message, Object... args) {

		System.err.format(message, args);

	}

}