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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Util to perform cleanup tasks when certain objects are garbage collected.
 */
public class ReferenceQueueCleaner {

    /**
     * Ref cleaner called when the referent is no longer reachable by gc.
     */
    public static abstract class CleanerRef<T> extends WeakReference<T> {

        public CleanerRef(T referent) {
            super(referent, (ReferenceQueue<T>) cleanerQueue);

            if (!init) {
                init();
            }

            if (shutdown) {
                throw new IllegalStateException("JVM is shutting down.");
            }


            cRefs.add(this);
        }

        public abstract void clean();

        @Override
        public void clear() {
            cRefs.remove(this);
            super.clear();
            try {
                clean();
            } catch (Throwable exc) {
                //DC.
            }
        }

        @Override
        public final int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public final boolean equals(Object obj) {
            return obj == this;
        }
    }

    protected static class CleanerShutdownHook extends Thread {
        @Override
        public void run() {
            shutdown = true;
            List<CleanerRef> refs  = new ArrayList<>();
            synchronized (ReferenceQueueCleaner.class) {
                refs.addAll(cRefs);
                cRefs.clear();
            }

            for (CleanerRef ref : refs) {
                ref.clear();
            }
        }
    };

    protected static volatile boolean shutdown = false;
    protected static volatile boolean init = false;
    protected static final ReferenceQueue<?> cleanerQueue = new ReferenceQueue<>();
    //Set of all cleaner refs. This is needed so that the gc doesn't gc the cleaner itself.
    //The refQ poller will remove all encountered references from this set.
    protected static final Set<CleanerRef<?>> cRefs = Collections.newSetFromMap(new ConcurrentHashMap<CleanerRef<?>, Boolean>());
    protected static volatile CleanerShutdownHook shutdownHook;

    public static boolean isInit() {
        return init;
    }

    public synchronized static void init() {
        if (init) {
            return;
        }

        init(Executors.newSingleThreadExecutor());
    }

    public synchronized static void init(Executor executor) {
        if (init) {
            return;
        }


        executor.execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("luajLFS cleanup thread");
                while(true) {
                    Reference<?> ref = null;
                    try {
                        ref = cleanerQueue.remove();
                    } catch (InterruptedException e) {
                        //Best effort... Killing this thread is a very bad idea anyways...
                        init = false;
                        cRefs.clear();
                        return;
                    }

                    cRefs.remove(ref);
                    if (ref instanceof CleanerRef) {
                        try {
                            ((CleanerRef<?>) ref).clean();
                        } catch (Throwable exc) {
                            //DC.
                        }
                    }
                }
            }
        });


        if (shutdownHook == null) {
            shutdownHook = new CleanerShutdownHook();
            try {
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            } catch (Exception exc) {
                //DC
            }
        }



        init = true;
    }

}
