# LuajLFS
LuajLFS is a port of the C based LuaFileSystem library to luaj.

LuajLFS tries to use the same OS syscalls that C based LuaFileSystem uses whenever possible.
On supported operating systems compatibility should be very high.

For more information on LuaFileSystem and a documentation of the Lua API see:
https://github.com/keplerproject/luafilesystem/

## License
LuajLFS is released under the GNU Lesser General Public License Version 3. <br>
A copy of the GNU Lesser General Public License Version 3 can be found in the COPYING & COPYING.LESSER files.<br>

## Requirements
* Java 7 or newer
* LuaJ 3.0.1

## Usage:
Maven:
````
<dependency>
  <groupId>io.github.alexanderschuetz97</groupId>
  <artifactId>LuajLFS</artifactId>
  <version>1.2</version>
</dependency>
````

In Java:
````
Globals globals = JsePlatform.standardGlobals();
globals.load(new LuajLFSLib());
//.... (Standart LuaJ from this point)
globals.load(new InputStreamReader(new FileInputStream("test.lua")), "test.lua").call();
````
In test.lua:
````
local lfs = require('lfs')
for file in lfs.dir(".") do
    print(file)
end
````
## Important Implementation Details
#### IOLib
"globals.load(new LuajLFSLib());" overwrites the IO library provided by luaj to 
circumvent having to use reflection to access the JSEIOLibs RandomAccessFile for all LFS methods 
that require an open "file" as argument. The replacement implementation is functionally identical to the JSEIOLib.
If you have a custom implementation of an IOLib and would like to use it then overwrite 
io.github.alexanderschuetz97.luajlfs.LuajLFSLib.overwriteIOLib and make it either do nothing or load your IOLib here.
In order for LuajLFS to work with your IOLib out of the box the file object has to be userdata of RandomAccessfile (isuserdata(RandomAccessfile.class) + checkuserdata(RandomAccessfile.class))

#### chdir & relative paths
Since LuaJ supports running multiple concurrent Lua environments that should NOT affect each other calling
the native methods "chdir" or "SetCurrentDirectory" would break this principle since all Lua Environments would 
affect each other's work directory. Not to mention that this is heavily discouraged by the JVM specification. 
To solve this the work directory is purely virtual and tracked inside a variable in java. 
This means if Lua changes the work directory and calls a Java method (via luajava for example) that then does new File(".") then said File Object
would not be in the work directory that lua set, but rather in the work directory that the entire JVM uses. 
Same goes for any calls made to C based JNI libraries. 

The following lua methods are overwritten by default to use the virtual work directory of LuajLFS:
* os.remove
* io.open
* dofile
* loadfile

If you do not wish for LuajLFS to overload any one of those methods then subclass LuajLFSLib
and overwrite the appropriate overloading method and make it either NOOP or use your own custom implementation.

If you have any other JavaLib that relies on relative paths consider calling LuajLFSLib.getVirtualWorkDirectory() to
get the current virtual Lua work directory as a basis for calculating the relative path.

#### File locking
As mentioned before LuaJ allows for multiple concurrent Lua Environments. Unfortunately the OS/JVM that manages
FileLocks is unaware of this and will assign the locks to the JVM Process. This means all locks on files are
always shared between all Lua Environments running in the same JVM Process.

#### Windows file locking
C based LuaFileSystem uses the _locking syscall to lock files. (lua method: lfs.lock) 
Unfortunately windows has 2 different methods of interacting with files:
HANDLE's and FD's. C based lua uses FD's and the JVM (tested with OpenJDK 8) uses HANDLES's.
The syscalls to lock a file are different and behave different:
1. _locking uses FD's
2. LockFileEx + UnlockFileEx uses HANDLE's

LuajLFS will detect which way the JVM uses to implement the RandomAccessFile and will use the appropriate 
syscall to lock the file depending on the JVM implementation. Due to this, the methods may not behave identical to C
based LuaFileSystem depending on how the JVM implemented RandomAccessFile. The main difference is that _locking allow
the user to place a lock above a lock already owned by the process replacing the old lock with a new lock. LockFileEx
does not permit this.

#### Operating System differences
By default LuajLFS has 3 different modes of operation:
1. Windows mode Vista and newer (amd64 & i386)
2. Linux mode (amd64 & i386 & armhf & aarch64)
3. Unsupported OS mode

For more information in regard to which GLIBC version is required for Linux mode see:
https://github.com/AlexanderSchuetz97/JavaNativeUtils

If for some reason your system does not meet the requirements for Linux or Windows mode LuajLFS will automatically fall back to Unsupported OS mode.

In Windows and Linux mode LuajLFS should behave nearly identical to C based LuaFileSystem. 
(Some error messages & codes in some corner cases may be different since I was not able to reproduce them, this holds especially true for Windows)

In Unsupported OS mode the method lfs.lock file uses the java.nio.FileLock mechanism to lock files rather than OS syscalls.
Any locks obtained by this mechanism will not be visible by non-Java applications. 
This means that if you run LuajLFS on for example Mac and want to obtain a lock on a file then 
this lock will not be "visible" to a non-Java application 
(such as for example a C based Lua script locking the same file using C Based LuaFileSystem)

In Unsupported OS mode the methods lfs.attributes and lfs.symlinkattributes will return only the information which is
available by using the JSE Standard operations. Check java.nio.file.attribute.BasicFileAttributes for more info.
Any other information is set to 0 or an appropriate default value.
