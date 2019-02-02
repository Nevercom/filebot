package net.filebot.media;

import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.hash.VerificationUtilities.*;

import java.io.File;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import net.filebot.MetaAttributeView;
import net.filebot.hash.HashType;

public enum XattrChecksum {

	CRC32;

	private final Cache<File, String> cache = CacheBuilder.newBuilder().expireAfterAccess(24, TimeUnit.HOURS).build();

	public String computeIfAbsent(File file) throws Exception {
		return cache.get(file, () -> {
			if (!useExtendedFileAttributes()) {
				return computeHash(file, getHashType());
			}

			// read xattr
			MetaAttributeView xattr = new MetaAttributeView(file);

			String value = xattr.get(getKey());
			if (value != null) {
				return value;
			}

			// compute checksum
			value = computeHash(file, getHashType());

			// store checksum (and make sure Last-Modified date is not changed)
			long t = file.lastModified();
			try {
				xattr.put(getKey(), value); // may or may not change Last-Modified date
			} catch (Exception e) {
				debug.warning(cause("Failed to set xattr", e));
			} finally {
				file.setLastModified(t);
			}

			return value;
		});
	}

	public void clear(File file) {
		cache.invalidate(cache);

		if (useExtendedFileAttributes()) {
			try {
				new MetaAttributeView(file).put(getKey(), null);
			} catch (Exception e) {
				debug.warning(cause("Failed to set xattr", e));
			}
		}
	}

	private String getKey() {
		return name();
	}

	private HashType getHashType() {
		switch (this) {
		case CRC32:
			return HashType.SFV;
		}
		return null;
	}

}
