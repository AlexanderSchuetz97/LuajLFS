//
// Copyright Alexander Schütz, 2021
//
// This file is part of LuajLFS.
//
// LuajLFS is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LuajLFS is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// A copy of the GNU Lesser General Public License should be provided
// in the COPYING & COPYING.LESSER files in top level directory of LuajLFS.
// If not, see <https://www.gnu.org/licenses/>.
//
package io.github.alexanderschuetz97.luajlfs;

import io.github.alexanderschuetz97.luajfshook.api.LuaFileSystemHandler;
import io.github.alexanderschuetz97.luajfshook.api.LuaPath;
import io.github.alexanderschuetz97.luajfshook.api.LuaRandomAccessFile;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Fallback impl using only JSE standard mechanisms.
 * Provides the worst compatibility but is better than nothing.
 * Locking uses java locks that only other JVMs can see (using the {@link FileLock} mechanism)
 * lfs.c uses native locks from its respective OS that non lua/java applications can see just fine.
 * This is not possible with just the JSE.
 *
 * attributes & symlinkattributes only return what information is available using JSE Standard API.
 * All other method calls will use JVM error messages instead of errno's that lfs.c uses in case of error.
 * Compatibility to JSEIOLib file descriptors relies on Reflection API and will probably break in newer java releases (8+)
 * so make sure to load this library before doing any calls to IOLib so it can install its own IOLib that does not require reflection.
 */
public class LuajLFSJSE extends LuajLFSCommon {

    protected static final Varargs ERR_UNLOCKED = err("The lock is not held by this process");

    protected LuajLFSJSE(LuaFileSystemHandler dirHandler, Globals globals, LuaTable table) {
        load(dirHandler, globals, table);
    }

    @Override
    protected Varargs lock_dir(Varargs args) {
        LuaPath lock = resolve(args.checkjstring(1)).child("lockfile.lfs");

        try {
            lock.createNewFile();
        } catch (FileAlreadyExistsException e) {
            return ERR_FILE_EXISTS;
        } catch (IOException e) {
            return ioErr(e);
        }

        lock_dir_userdata userdata = new lock_dir_userdata(null);
        //GC Magic to clean it
        userdata.m_instance =  new lock_dir_cleaner(userdata, lock);

        return userdata;
    }

    @Override
    public boolean isAbsolute(String path) {
        //UNIX
        if (File.separatorChar == '/') {
            return path.startsWith("/");
        }

        //Windows

        // \\SOME-PATH-GOES-HERE\....
        if (path.startsWith("\\\\")) {
            return true;
        }

        //C:\...
        if (path.substring(1).startsWith(":\\")) {
            return true;
        }

        return false;
    }

    @Override
    protected Varargs ioErr(IOException exc) {
        String msg = exc.getMessage();
        if (msg == null) {
            return INPUT_OUTPUT_ERROR;
        }

        return err("Input/output error: " + msg, 5);
    }

    @Override
    protected Varargs attributes(Varargs args) {
        BasicFileAttributes bfa = null;

        try {
            bfa = resolve(args.checkjstring(1)).attributes();
        } catch (NoSuchFileException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (IOException e) {
            return ioErr(e);
        }

        return mapStatResult(args.arg(2), bfa);
    }

    @Override
    protected Varargs symlinkattributes(Varargs args) {
        BasicFileAttributes bfa = null;

        try {
            bfa = resolve(args.checkjstring(1)).linkAttributes();
        } catch (NoSuchFileException e) {
            return ERR_NO_SUCH_FILE_OR_DIR;
        } catch (IOException e) {
            return ioErr(e);
        }

        return mapStatResult(args.arg(2), bfa);
    }







    @Override
    protected Varargs link(Varargs args) {

        LuaPath target = resolve(args.checkjstring(1));
        LuaPath link = resolve(args.checkjstring(2));
        try {
            if (args.checkboolean(3)) {
                link.symlink(target);
            } else {
                link.link(target);
            }
        } catch (FileAlreadyExistsException e) {
            return ERR_FILE_EXISTS;
        } catch (IOException e) {
            return ioErr(e);
        }

        return LuaValue.TRUE;
    }

    protected static class LockKey {
        protected final FileChannel channel;
        protected final long start;
        protected final long len;

        public LockKey(FileChannel channel, long start, long len) {
            this.channel = Objects.requireNonNull(channel);
            this.start = start;
            this.len = len;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LockKey lockKey = (LockKey) o;

            if (start != lockKey.start) {
                return false;
            }

            if (len != lockKey.len) {
                return false;
            }

            return channel == lockKey.channel;
        }

        @Override
        public int hashCode() {
            int result = channel.hashCode();
            result = 31 * result + (int) (start ^ (start >>> 32));
            result = 31 * result + (int) (len ^ (len >>> 32));
            return result;
        }
    }

    protected static class LockCleaner extends ReferenceQueueCleaner.CleanerRef<LuaValue> {

        private final ConcurrentMap<LockKey, LockCleaner> map;
        private final LockKey key;
        private final FileLock lock;

        public LockCleaner(LuaValue referent, ConcurrentMap<LockKey, LockCleaner> map, LockKey key, FileLock lock) {
            super(referent);
            this.map = Objects.requireNonNull(map);
            this.key = Objects.requireNonNull(key);
            this.lock = Objects.requireNonNull(lock);
        }

        @Override
        public void clean() {
            map.remove(key, this);
            try {
                lock.release();
            } catch (IOException e) {
                //DC
            }
        }
    }


    private final ConcurrentMap<LockKey, LockCleaner> fileLockTable = new ConcurrentHashMap<>();

    @Override
    protected Varargs lockExclusive(LuaValue userdata, LuaRandomAccessFile fileDescriptor, long start, long len) {

        FileChannel channel = fileDescriptor.getFileChannel();
        if (channel == null) {
            return ERR_NOT_SUPPORTED;
        }

        FileLock lock;
        try {
            lock = channel.tryLock(start, len, false);
            if (lock == null) {
                return ERR_LOCK_LOCKED;
            }
        } catch (OverlappingFileLockException e) {
            return ERR_LOCK_LOCKED;
        } catch (IOException e) {
            return ioErr(e);
        }

        LockKey key = new LockKey(channel, start, len);
        LockCleaner cleaner = new LockCleaner(userdata, fileLockTable, key, lock);
        LockCleaner other = fileLockTable.put(key, cleaner);
        if (other != null) {
            other.clear();
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockShared(LuaValue userdata, LuaRandomAccessFile fileDescriptor, long start, long len) {
        FileChannel channel = fileDescriptor.getFileChannel();
        if (channel == null) {
            return ERR_NOT_SUPPORTED;
        }

        FileLock lock;
        try {
            lock = channel.tryLock(start, len, true);
            if (lock == null) {
                return ERR_LOCK_LOCKED;
            }
        } catch (OverlappingFileLockException e) {
            return ERR_LOCK_LOCKED;
        } catch (IOException e) {
            return ioErr(e);
        }

        LockKey key = new LockKey(channel, start, len);
        LockCleaner cleaner = new LockCleaner(userdata, fileLockTable, key, lock);
        LockCleaner other = fileLockTable.put(key, cleaner);
        if (other != null) {
            other.clear();
        }

        return LuaValue.TRUE;
    }

    @Override
    protected Varargs lockUnlock(LuaValue userdata, LuaRandomAccessFile fileDescriptor, long start, long len) {
        FileChannel channel = fileDescriptor.getFileChannel();
        if (channel == null) {
            return ERR_NOT_SUPPORTED;
        }

        LockCleaner cleaner = fileLockTable.remove(new LockKey(channel, start, len));
        if (cleaner != null) {
            cleaner.clear();
            return LuaValue.TRUE;
        }

        return ERR_UNLOCKED;


    }


}
