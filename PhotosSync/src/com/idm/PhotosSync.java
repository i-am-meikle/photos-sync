package com.idm;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PhotosSync {

	public static void main(String[] args) throws IOException {

		new PhotosSync().sync(args);
		System.exit(0);

	}

	private String source;
	private String dest;
	private Path sourcePath;
	private Path destPath;

	private void sync(String[] args) throws IOException {

		// This code assume the file structure of the source
		// Photos Library is /Year/Month/Day/Time/filename

		this.log("Starting sync");
		long startTime = System.currentTimeMillis();
		source = args[0];
		dest = args[1];

		this.log("Syncing Photos from Library: " + source + " to: " + dest);

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

		DirectoryStream<Path> dir = Files.newDirectoryStream(sourcePath);
		for (Path child : dir) {

			if (Files.isDirectory(child)) {
				this.syncYear(child);
			} else {
				this.syncFile(child, destPath);
			}
		}
		dir.close();
		long timeTaken = System.currentTimeMillis() - startTime;
		this.log("Finished sync in: " + String.valueOf(timeTaken) + "ms");

	}

	private void syncFile(Path source, Path target) throws IOException {

		Path targetFile = target.resolve(source.getFileName());
		if (!Files.exists(targetFile)) {
			this.log("Copying file: " + source + " to: " + targetFile);
			Files.copy(source, targetFile, new CopyOption[0]);
		}
	}

	private void syncYear(Path sourceYearPath) throws IOException {

		Path targetYearPath = destPath.resolve(sourcePath.relativize(sourceYearPath));
		if (!Files.exists(targetYearPath)) {
			this.log("Creating directory: " + targetYearPath);
			Files.copy(sourcePath, targetYearPath, new CopyOption[0]);
		}

		DirectoryStream<Path> dir = Files.newDirectoryStream(sourceYearPath);
		for (Path child : dir) {

			if (Files.isDirectory(child)) {
				this.syncMonth(child);
			} else {
				this.syncFile(child, targetYearPath);
			}
		}
		dir.close();
		
	}

	private void syncMonth(Path sourceFile) throws IOException {

		Path target = destPath.resolve(sourcePath.relativize(sourceFile));
		if (!Files.exists(target)) {
			this.log("Creating directory: " + target);
			Files.copy(sourceFile, target, new CopyOption[0]);
		}

		DirectoryStream<Path> dir = Files.newDirectoryStream(sourceFile);
		for (Path child : dir) {

			if (Files.isDirectory(child)) {
				this.syncDay(child);
			} else {
				this.syncFile(child, target);
			}
		}
		dir.close();

	}

	private void syncDay(Path sourceFile) throws IOException {

		Path target = destPath.resolve(sourcePath.relativize(sourceFile));
		if (!Files.exists(target)) {
			this.log("Creating directory: " + target);
			Files.copy(sourceFile, target, new CopyOption[0]);
		}
		
		collapseDirectory(sourceFile, target);

	}

	private void collapseDirectory(Path sourceFile, Path target) throws IOException {

		DirectoryStream<Path> dir = Files.newDirectoryStream(sourceFile);
		for (Path child : dir) {

			if (Files.isDirectory(child)) {
				this.collapseDirectory(child, target);
			} else {
				this.syncFile(child, target);
			}
		}
		dir.close();

	}

	private void log(String string) {

		System.out.println(string);

	}

}
