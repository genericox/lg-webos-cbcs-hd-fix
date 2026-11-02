// Temporary compatibility experiment. Preserves CDM, keys, license enforcement,
// protected payloads, native crypto calls and their error handling.
// @category RaceWeekend
import ghidra.app.script.GhidraScript;
import ghidra.app.plugin.assembler.*;
import ghidra.program.model.address.*;
import ghidra.app.emulator.EmulatorHelper;
import ghidra.program.model.lang.ProcessorContextImpl;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
public class PatchC9CbcsCounter extends GhidraScript {
 public void run() throws Exception {
  String[] args=getScriptArgs();
  byte[] original=Files.readAllBytes(Paths.get(args[0]));
  Address address=toAddr(0x11370);
  byte[] before=new byte[28];currentProgram.getMemory().getBytes(address,before);
  byte[] expected=ghidra.util.NumericUtilities.convertStringToBytes("fff7e0ed3b49a9230d9a7944fff794ed079a5c4655f8083c1a440792");
  if(!Arrays.equals(before,expected)||!Arrays.equals(before,Arrays.copyOfRange(original,0x1370,0x138c)))throw new IllegalStateException("Original instructions differ");
  Assembler assembler=Assemblers.getAssembler(currentProgram);
  assembler.assemble(address,
    "ldr r2,[sp,#0x1c]",
    "mov r4,r11",
    "ldr.w r3,[r5,#-0x8]",
    "add r2,r3",
    "ldr.w r0,[r9,#0x10]",
    "cmp r0,#0x1",
    "bne 0x1138a",
    "ldr.w r0,[r9,#0x14]",
    "cbz r0,0x1138a",
    "movs r2,#0x0",
    "str r2,[sp,#0x1c]");
  byte[] after=new byte[28];currentProgram.getMemory().getBytes(address,after);
  // The store remains the final instruction at 0x1138a. Pointer accounting and
  // every failure branch below 0x1138c must remain untouched.
  if(after[26]!=(byte)0x07||after[27]!=(byte)0x92)throw new IllegalStateException("Unexpected assembly layout");
  // Execute only the patched counter block in Ghidra's emulator with synthetic
  // integers. No native crypto calls, device data, keys or media buffers exist.
  int[][] cases={{0,0,34,100,134},{1,0,34,100,134},{1,1,0,127461,0},{1,1,0,120537,0}};
  for(int[] test:cases){
    EmulatorHelper emulator=new EmulatorHelper(currentProgram);
    try{
      emulator.writeRegister("sp",0x800000);
      emulator.writeRegister("r9",0x810000);
      emulator.writeRegister("r5",0x820008);
      emulator.writeRegister("r11",test[2]>>>4);
      emulator.writeRegister("cpsr",0x20);
      emulator.writeMemory(toAddr(0x80001c),le32(test[2]));
      emulator.writeMemory(toAddr(0x810010),le32(test[0]));
      emulator.writeMemory(toAddr(0x810014),le32(test[1]));
      emulator.writeMemory(toAddr(0x820000),le32(test[3]));
      // Stop before the unchanged final STR. Legacy Ghidra ARM emulation has
      // a decode issue at that branch target; validate its unchanged bytes above.
      emulator.setBreakpoint(toAddr(0x1138a));
      ProcessorContextImpl context=new ProcessorContextImpl(currentProgram.getLanguage());
      context.setValue(currentProgram.getRegister("TMode"),BigInteger.ONE);
      if(!emulator.run(address,context,monitor))throw new IllegalStateException("Emulation failed: "+emulator.getLastError());
      if(emulator.readRegister("r2").intValue()!=test[4]||emulator.readRegister("r3").intValue()!=test[3]||emulator.readRegister("r4").intValue()!=(test[2]>>>4))throw new IllegalStateException("Counter/pointer regression in synthetic case");
      println("Emulated mode="+test[0]+" pattern="+test[1]+" input="+test[2]+" length="+test[3]+" output="+test[4]);
    }finally{emulator.dispose();}
  }
  byte[] patched=original.clone();System.arraycopy(after,0,patched,0x1370,28);
  Path output=Paths.get(args[1]);
  if(Files.exists(output))throw new IllegalStateException("Output already exists");
  Files.write(output,patched);
  println("Patched bytes: "+ghidra.util.NumericUtilities.convertBytesToString(after));
  println("Only 28-byte success-log/counter block replaced; original file unchanged");
 }
 private byte[] le32(int value){return new byte[]{(byte)value,(byte)(value>>>8),(byte)(value>>>16),(byte)(value>>>24)};}
}
