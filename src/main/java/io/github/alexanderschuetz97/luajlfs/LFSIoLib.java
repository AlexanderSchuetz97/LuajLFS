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

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.lib.IoLib;
import org.luaj.vm2.lib.jse.JseIoLib;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class LFSIoLib extends JseIoLib {

    private final LuajLFSCommon common;

    public LFSIoLib(LuajLFSCommon common) {
        this.common = common;
    }

    protected File openFile( String filename, boolean readMode, boolean appendMode, boolean updateMode, boolean binaryMode ) throws IOException {
        filename = common.resolve(filename).getAbsolutePath();

        RandomAccessFile f = new RandomAccessFile(filename,readMode? "r": "rw");
        if (!appendMode && !readMode) {
            f.setLength(0);
        } else if (appendMode) {
            f.seek(f.length());
        }

        return new RandomAccessFileFile(f);
    }

    protected File openProgram(String prog, String mode) throws IOException {
        //This is pretty bad ngl... I would like to improve this to make
        //it not be like this but can not due to api...
        final Process p = Runtime.getRuntime().exec(prog, null, common.getPwd());
        return "w".equals(mode)?
                new OutputStreamFile( p.getOutputStream() ):
                new InputStreamFile( p.getInputStream() );
    }

    protected File tmpFile() throws IOException {
        java.io.File f = java.io.File.createTempFile(".luaj","bin");
        f.deleteOnExit();
        return new RandomAccessFileFile( new RandomAccessFile(f,"rw") );
    }

    protected class OutputStreamFile extends IoLib.File {

        private final OutputStream outputStream;
        private boolean closed = false;
        private boolean flushAfterWrite = false;

        public OutputStreamFile(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void write(LuaString string) throws IOException {
            outputStream.write( string.m_bytes, string.m_offset, string.m_length );
            if (flushAfterWrite) {
                flush();
            }
        }

        @Override
        public void flush() throws IOException {
            outputStream.flush();
        }

        @Override
        public boolean isstdfile() {
            return true;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            outputStream.close();

        }

        @Override
        public boolean isclosed() {
            return closed;
        }

        @Override
        public int seek(String option, int bytecount) throws IOException {
            throw new LuaError("not implemented");
        }

        @Override
        public void setvbuf(String mode, int size) {
            flushAfterWrite = "no".equals(mode);
        }

        @Override
        public int remaining() throws IOException {
            return -1;
        }

        @Override
        public int peek() throws IOException, EOFException {
            throw new LuaError("not implemented");
        }

        @Override
        public int read() throws IOException, EOFException {
            throw new LuaError("not implemented");
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            throw new LuaError("not implemented");
        }
    }

    protected class InputStreamFile extends IoLib.File {

        private final InputStream inputStream;
        private boolean closed = false;

        public InputStreamFile(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void write(LuaString string) throws IOException {
            throw new LuaError("not implemented");
        }

        @Override
        public void flush() throws IOException {

        }

        @Override
        public boolean isstdfile() {
            return true;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            inputStream.close();

        }

        @Override
        public boolean isclosed() {
            return closed;
        }

        @Override
        public int seek(String option, int bytecount) throws IOException {
            throw new LuaError("not implemented");
        }

        @Override
        public void setvbuf(String mode, int size) {

        }

        @Override
        public int remaining() throws IOException {
            return -1;
        }

        @Override
        public int peek() throws IOException, EOFException {
            inputStream.mark(1);
            int c = inputStream.read();
            inputStream.reset();
            return c;
        }

        @Override
        public int read() throws IOException, EOFException {
            return inputStream.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return inputStream.read(bytes, offset, length);
        }
    }

    protected class RandomAccessFileFile extends IoLib.File {

        private final RandomAccessFile file;
        private boolean closed = false;

        public RandomAccessFileFile(RandomAccessFile file) throws IOException {
            this.file = file;
        }

        @Override
        public boolean isuserdata() {
            return true;
        }

        @Override
        public boolean isuserdata(Class c) {
            if (c.isInstance(file)) {
                return true;
            }

            return false;
        }

        @Override
        public Object touserdata() {
            return file;
        }

        @Override
        public Object touserdata(Class c) {
            if (!c.isInstance(file)) {
                return null;
            }

            return file;
        }

        @Override
        public Object checkuserdata() {
            return file;
        }

        @Override
        public Object checkuserdata(Class c) {

            if (c.isInstance(file)) {
                return file;
            }

            return typerror(c.getName());
        }

        @Override
        public void write(LuaString string) throws IOException {
            file.write(string.m_bytes, string.m_offset, string.m_length);
        }

        @Override
        public void flush() throws IOException {
            file.getFD().sync();
        }

        @Override
        public boolean isstdfile() {
            return false;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            file.close();
        }

        @Override
        public boolean isclosed() {
            return closed;
        }

        @Override
        public int seek(String option, int bytecount) throws IOException {
            switch (option) {
                case ("set"):
                    file.seek(bytecount);
                    break;
                case ("end"):
                    file.seek(file.length()+bytecount);
                    break;
                default:
                    file.seek(file.getFilePointer()+bytecount);
                    break;
            }

            return (int) file.getFilePointer();
        }

        @Override
        public void setvbuf(String mode, int size) {

        }

        @Override
        public int remaining() throws IOException {
            return (int) (file.length()-file.getFilePointer());
        }

        @Override
        public int peek() throws IOException, EOFException {
            long fp = file.getFilePointer();
            int c = file.read();
            file.seek(fp);
            return c;
        }

        @Override
        public int read() throws IOException, EOFException {
            return file.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return file.read(bytes, offset, length);
        }
    }



}
