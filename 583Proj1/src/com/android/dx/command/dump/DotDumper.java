/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dx.command.dump;

import java.io.BufferedWriter;

import com.android.dx.cf.code.ConcreteMethod;
import com.android.dx.cf.code.Ropper;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.cf.iface.Member;
import com.android.dx.cf.iface.Method;
import com.android.dx.cf.iface.ParseObserver;
import com.android.dx.rop.code.AccessFlags;
import com.android.dx.rop.code.BasicBlock;
import com.android.dx.rop.code.BasicBlockList;
import com.android.dx.rop.code.DexTranslationAdvice;
import com.android.dx.rop.code.Insn;
import com.android.dx.rop.code.InsnList;
import com.android.dx.rop.code.RopMethod;
import com.android.dx.rop.code.TranslationAdvice;
import com.android.dx.ssa.Optimizer;
import com.android.dx.util.ByteArray;
import com.android.dx.util.Hex;
import com.android.dx.util.IntList;

/**
 * Dumps the pred/succ graph of methods into a format compatible
 * with the popular graph utility "dot".
 */
public class DotDumper implements ParseObserver {
    private DirectClassFile classFile;

    private final byte[] bytes;
    private final String filePath;
    private final boolean strictParse;
    private final boolean optimize;
    private final Args args;
    
    public BufferedWriter writerGraph;
    
    
    public BufferedWriter writerInstr;
    

    static void dump(byte[] bytes, String filePath, Args args) {
        new DotDumper(bytes, filePath, args).run();   
    }

    DotDumper(byte[] bytes, String filePath, Args args) {
        this.bytes = bytes;
        this.filePath = filePath;
        this.strictParse = args.strictParse;
        this.optimize = args.optimize;
        this.args = args;
        
        
    }
    

    private void run() {
        ByteArray ba = new ByteArray(bytes);

        /*
         * First, parse the file completely, so we can safely refer to
         * attributes, etc.
         */
        classFile = new DirectClassFile(ba, filePath, strictParse);
        classFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
        classFile.getMagic(); // Force parsing to happen.

        // Next, reparse it and observe the process.
        DirectClassFile liveCf =
            new DirectClassFile(ba, filePath, strictParse);
        liveCf.setAttributeFactory(StdAttributeFactory.THE_ONE);
        liveCf.setObserver(this);
        liveCf.getMagic(); // Force parsing to happen.
    }

    /**
     * @param name method name
     * @return true if this method should be dumped
     */
    protected boolean shouldDumpMethod(String name) {
        return args.method == null || args.method.equals(name);
    }

    public void changeIndent(int indentDelta) {
        // This space intentionally left blank.
    }

    public void parsed(ByteArray bytes, int offset, int len, String human) {
        // This space intentionally left blank.
    }

    /** {@inheritDoc} */
    public void startParsingMember(ByteArray bytes, int offset, String name,
                                   String descriptor) {
        // This space intentionally left blank.
    }

    public void endParsingMember(ByteArray bytes, int offset, String name,
                                 String descriptor, Member member) {
        try{
    	if (!(member instanceof Method)) {
            return;
        }

        if (!shouldDumpMethod(name)) {
            return;
        }

        ConcreteMethod meth = new ConcreteMethod((Method) member, classFile,
                                                 true, true);
        
        TranslationAdvice advice = DexTranslationAdvice.THE_ONE;
        RopMethod rmeth =
            Ropper.convert(meth, advice);

        if (optimize) {
            boolean isStatic = AccessFlags.isStatic(meth.getAccessFlags());
            rmeth = Optimizer.optimize(rmeth,
                    BaseDumper.computeParamWidth(meth, isStatic), isStatic,
                    true, advice);
        }

        System.out.println("digraph "  + name + "{");

        System.out.println("\tfirst -> n"
                + Hex.u2(rmeth.getFirstLabel()) + ";");
        
        

        BasicBlockList blocks = rmeth.getBlocks();
        int sz = blocks.size();
        String preHeader = String.format("%s\t%s", meth.getDefiningClass().toHuman(), meth.getNat().toHuman());
        
        //ByteBlockList list = BasicBlocker.identifyBlocks(meth);
        //Assert.assertEquals(list.size(),blocks.size());
        //dumpBBInstructions(list,methBytes, meth,code);
        
        for (int i = 0; i < sz; i++) {
            BasicBlock bb = blocks.get(i);
            writerInstr.write(dumpBlock(preHeader, bb));
            int label = bb.getLabel();
            IntList successors = bb.getSuccessors();

            System.out.println(Hex.u2(bb.getLabel())+"\t"+bb.getInsns().get(0));
            if (successors.size() == 0) {
                System.out.println("\tn" + Hex.u2(label) + " -> returns;");
            } else if (successors.size() == 1) {
                System.out.println("\tn" + Hex.u2(label) + " -> n"
                        + Hex.u2(successors.get(0)) + ";");
                writerGraph.write(Hex.u2(label)+"\t"+ Hex.u2(successors.get(0))+"\n");
            } else {
                System.out.print("\tn" + Hex.u2(label) + " -> {");
                for (int j = 0; j < successors.size(); j++ ) {
                    int successor = successors.get(j);
                    writerGraph.write(Hex.u2(label)+"\t"+ Hex.u2(successor)+"\n");
                    if (successor != bb.getPrimarySuccessor()) {
                    	System.out.print(" n" + Hex.u2(successor) + " ");
                    }

                }
                System.out.println("};");

                System.out.println("\tn" + Hex.u2(label) + " -> n"
                        + Hex.u2(bb.getPrimarySuccessor())
                        + " [label=\"primary\"];");


            }
        }

        System.out.println("}");
        }catch(Exception e){
        	e.printStackTrace();
        	System.exit(0);
        }
    }
    
    private String dumpBlock(String header, BasicBlock bb)
    {
    	String out="";
    	String label = Hex.u2(bb.getLabel());
    	
    	InsnList iList = bb.getInsns();
    	String tmpStr="";
    	int eCount = 0;	
    	for(int i=0; i< iList.size(); i++)
    	{
    		Insn ins = iList.get(i);
    		tmpStr += ins.getOpcode().opcode+"";
    		eCount+= ins.getOpcode().exceptions.size();
    		if(i != iList.size() - 1)
    		{
    			tmpStr += ",";
    		}
    	}
    	out = String.format("%s\t%s\t%s\t%d\n", header, label, tmpStr, eCount);
    	return out;
    }
    
    
    
//    void  dumpBBInstructions(ByteBlockList list,ByteArray bytes,ConcreteMethod meth, BytecodeArray code){
//    	
//    	int sz = list.size();
//        CodeObserver codeObserver = new CodeObserver(bytes, DotDumper.this);
//
//        // Reset the dump cursor to the start of the bytecode.
//        //setAt(bytes, 0);
//
//        int byteAt = 0;
//        for (int i = 0; i < sz; i++) {
//            ByteBlock bb = list.get(i);
//            int start = bb.getStart();
//            int end = bb.getEnd();
//
//            if (byteAt < start) {
//                parsed(bytes, byteAt, start - byteAt,
//                       "dead code " + Hex.u2(byteAt) + ".." + Hex.u2(start));
//            }
//
//            parsed(bytes, start, 0,
//                    "block " + Hex.u2(bb.getLabel()) + ": " +
//                    Hex.u2(start) + ".." + Hex.u2(end));
//            changeIndent(1);
//
//            int len;
//            for (int j = start; j < end; j += len) {
//                len = code.parseInstruction(j, codeObserver);
//            
//                codeObserver.setPreviousOffset(j);
//            }
//
//            IntList successors = bb.getSuccessors();
//            int ssz = successors.size();
//            if (ssz == 0) {
//                parsed(bytes, end, 0, "returns");
//            } else {
//                for (int j = 0; j < ssz; j++) {
//                    int succ = successors.get(j);
//                    parsed(bytes, end, 0, "next " + Hex.u2(succ));
//                }
//            }
//
//            ByteCatchList catches = bb.getCatches();
//            int csz = catches.size();
//            for (int j = 0; j < csz; j++) {
//                ByteCatchList.Item one = catches.get(j);
//                CstType exceptionClass = one.getExceptionClass();
//                parsed(bytes, end, 0,
//                       "catch " +
//                       ((exceptionClass == CstType.OBJECT) ? "<any>" :
//                        exceptionClass.toHuman()) + " -> " +
//                       Hex.u2(one.getHandlerPc()));
//            }
//
//            changeIndent(-1);
//            byteAt = end;
//        }
//
//        int end = bytes.size();
//        if (byteAt < end) {
//            parsed(bytes, byteAt, end - byteAt,
//                    "dead code " + Hex.u2(byteAt) + ".." + Hex.u2(end));
//        }
//
//    	
//    }
//    
//    
    
}
