package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;


public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	Stack<Number> constants = new Stack<>();
	private boolean isOptimised = false;
	private boolean passedIf = false;
	private int ifTarget = -1000;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	private void arithmetic(Instruction instruction) {
		Number one = constants.pop();
		Number two = constants.pop();
		Number result = null;

		if (instruction instanceof IADD) {
			result = one.intValue() + two.intValue();
		}
		else if (instruction instanceof DADD) {
			result = one.doubleValue() + two.doubleValue();
		}
		else if (instruction instanceof FADD) {
			result = one.floatValue() + two.floatValue();
		}
		else if (instruction instanceof LADD) {
			result = one.longValue() + two.longValue();
		}
		else if (instruction instanceof ISUB) {
			result = two.intValue() - one.intValue();
		}
		else if (instruction instanceof DSUB) {
			result = two.doubleValue() - one.doubleValue();
		}
		else if (instruction instanceof FSUB) {
			result = two.floatValue() - one.floatValue();
		}
		else if (instruction instanceof LSUB) {
			result = two.longValue() - one.longValue();
		}
		else if (instruction instanceof IMUL) {
			result = one.intValue() * two.intValue();
		}
		else if (instruction instanceof DMUL) {
			result = one.doubleValue() * two.doubleValue();
		}
		else if (instruction instanceof FMUL) {
			result = one.floatValue() * two.floatValue();
		}
		else if (instruction instanceof LMUL) {
			result = one.longValue() * two.longValue();
		}
		else if (instruction instanceof IDIV) {
			result = one.intValue() / two.intValue();
		}
		else if (instruction instanceof DDIV) {
			result = one.doubleValue() / two.doubleValue();
		}
		else if (instruction instanceof FDIV) {
			result = one.floatValue() / two.floatValue();
		}
		else if (instruction instanceof LDIV) {
			result = one.longValue() / two.longValue();
		}

		if (result != null) {
			constants.push(result);
			//System.out.println(constants);
		}
	}


	private InstructionHandle doLDC(ConstantPoolGen cpgen, InstructionList instructionList, InstructionHandle handle, boolean ifDelete) {
		Instruction instruction = handle.getInstruction();
		Number number = null;

		if (instruction instanceof LDC) {
			number = (Number) ((LDC) instruction).getValue(cpgen);
		}
		else if (instruction instanceof LDC2_W) {
			number = ((LDC2_W) instruction).getValue(cpgen);
		}
		else if (instruction instanceof BIPUSH) {
			number = ((BIPUSH) instruction).getValue();
		}
		else if (instruction instanceof SIPUSH) {
			number = ((SIPUSH) instruction).getValue();
		}

		InstructionHandle LDCNext = handle.getNext();
		if (ifDelete) {
			try {
				instructionList.delete(handle);
			} catch (TargetLostException e) {
				e.printStackTrace();
			}
		}
		else {
			if (!(instruction instanceof LDC2_W)) {
				if (number instanceof Integer) instructionList.insert(handle,new LDC(cpgen.addInteger((Integer) number)));
				else if (number instanceof Float) { instructionList.insert(handle, new LDC(cpgen.addFloat((Float) number)));
				}
			}
			else {
				if (number instanceof Double) { instructionList.insert(handle, new LDC2_W(cpgen.addDouble((Double) number))); }
				else if (number instanceof Long) { instructionList.insert(handle, new LDC2_W(cpgen.addLong((Long) number))); }
			}
			try {
				instructionList.delete(handle);
			} catch (TargetLostException e) {
				e.printStackTrace();
			}
		}
		constants.push(number); // push when get constants

		return LDCNext;
	}

	private void optimisation(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
		InstructionList instructionList = new InstructionList(method.getCode().getCode());
		MethodGen methodGen = new MethodGen(method.getAccessFlags(),method.getReturnType(),
				method.getArgumentTypes(),null,method.getName(),
				cgen.getClassName(),instructionList,cpgen);

//		System.out.println(method.getName());
//		System.out.println(methodGen.getInstructionList());

		int before = instructionList.getLength();

		InstructionHandle LDCNext = null;
		HashMap<Integer,Number> storeValues = new HashMap<>();
		boolean isIfTarget = false;

		for (InstructionHandle handle : instructionList.getInstructionHandles()) {
			Instruction instruction = handle.getInstruction();

			//Get current instruction type
			boolean isLDC = (instruction instanceof LDC || instruction instanceof LDC2_W
							|| instruction instanceof BIPUSH || instruction instanceof SIPUSH);
			boolean isArithmetic = (instruction instanceof ArithmeticInstruction);
			boolean isStore = (instruction instanceof StoreInstruction);
			boolean isConstant = (instruction instanceof ConstantPushInstruction
							&& !(instruction instanceof BIPUSH || instruction instanceof SIPUSH));
			boolean isI2D = (instruction instanceof I2D);
			boolean isLoad = (instruction instanceof LoadInstruction);
			boolean isLCMP = (instruction instanceof LCMP);
			boolean isCMP = (instruction instanceof IfInstruction);
			boolean isGOTO = (instruction instanceof GOTO);


			if (isLDC && (handle.getNext().getInstruction() instanceof StoreInstruction)) {
				LDCNext = doLDC(cpgen,instructionList,handle,false);
//				System.out.println("After store instruction: " +constants);
			}
			else if (isLDC) {
				LDCNext = doLDC(cpgen,instructionList,handle,true);
			}

			else if (isStore) {
				Number value = constants.pop(); // pop when store
				Integer index = ((StoreInstruction)instruction).getIndex();
				storeValues.put(index,value);
//				System.out.println("Value to store: " +index+">>>"+value);
			}

			else if (isConstant && !(handle.getNext().getInstruction() instanceof StoreInstruction)) {
				if (passedIf && handle.getPosition() == ifTarget) {
					instructionList.insert(handle,new ICONST((Integer) constants.pop()));
					try {
						instructionList.delete(handle);
					} catch (TargetLostException e) {
						e.printStackTrace();
					}
					ifTarget = -1000;
				}
				else if (passedIf && !(handle.getNext().getInstruction() instanceof ReturnInstruction)) {
					try {
						instructionList.delete(handle);
					} catch (TargetLostException e) {
						e.printStackTrace();
					}
				}
				else if (!passedIf) {
					constants.push(((ConstantPushInstruction)instruction).getValue());
					try {
						instructionList.delete(handle);
					} catch (TargetLostException e) {
						e.printStackTrace();
					}
				}
			}
			else if (isConstant){
				Number value = ((ConstantPushInstruction)instruction).getValue();
				constants.push(value);
				//System.out.println("Get constant: "+constants);
				InstructionHandle insertPoint = handle.getNext();
				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}

				if (value instanceof Integer) { instructionList.insert(insertPoint, new LDC(cpgen.addInteger((Integer) value))); }
				else if (value instanceof Double) { instructionList.insert(insertPoint, new LDC2_W(cpgen.addDouble((Double) value))); }
				else if (value instanceof Float) { instructionList.insert(insertPoint, new LDC(cpgen.addFloat((Float) value))); }
				else if (value instanceof Long) { instructionList.insert(insertPoint, new LDC2_W(cpgen.addLong((Long) value))); }
			}

			else if (isI2D) {
				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}

			else if (isArithmetic) {
				if (constants.size() >= 2) {
					arithmetic(instruction);
					InstructionHandle insertPoint = handle.getNext();
					try {
						instructionList.delete(handle);
					} catch (TargetLostException e) {
						e.printStackTrace();
					}

					Number value = constants.pop();
					if (value instanceof Integer) { instructionList.insert(insertPoint, new LDC(cpgen.addInteger((Integer) value))); }
					else if (value instanceof Double) { instructionList.insert(insertPoint, new LDC2_W(cpgen.addDouble((Double) value))); }
					else if (value instanceof Float) { instructionList.insert(insertPoint, new LDC(cpgen.addFloat((Float) value))); }
					else if (value instanceof Long) { instructionList.insert(insertPoint, new LDC2_W(cpgen.addLong((Long) value))); }
					if (!(insertPoint.getInstruction() instanceof ReturnInstruction)) constants.push(value);
//					System.out.println("ccc:  " + constants);
				}
			}

			else if (isLoad && !(instruction instanceof ALOAD)) {
				int stackIndex = ((LoadInstruction)instruction).getIndex();
//				System.out.println(stackIndex);
				constants.push(storeValues.get(stackIndex));
			}

			else if (isCMP) {
				if (instruction instanceof IF_ICMPLE) {
					Number y = constants.pop();
					Number x = constants.pop();
//					System.out.println("x: "+x+", y: "+y);
					if ((Integer) x <= (Integer) y) {
						constants.push(0);
					}
					else {
						constants.push(1);
					}
				}
				else if (instruction instanceof IFLE) {
					Number value = constants.pop();

					if ((Integer)value == 1) {
						constants.push(1);
					}
					else {
						constants.push(0);
					}
				}

				passedIf = true;
				ifTarget = ((IfInstruction) instruction).getTarget().getPosition();

				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}

			else if (isGOTO) {
				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}

			else if (isLCMP && constants.size() >= 2) {
				Number one = constants.pop();
				Number two = constants.pop();
				if ((Long) one > (Long) two) {
					constants.push(-1);
				}
				else if ((Long) one < (Long) two) {
					constants.push(1);
				}
				else {
					constants.push(0);
				}

				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}
		}

		//System.out.println("constants size: "+constants.size());
		if (constants.size() > 0 && !passedIf) {
			Number value = constants.pop();
			if (value instanceof Integer) { instructionList.insert(LDCNext, new LDC(cpgen.addInteger((Integer) value))); }
			else if (value instanceof Double) { instructionList.insert(LDCNext, new LDC2_W(cpgen.addDouble((Double) value))); }
			else if (value instanceof Float) { instructionList.insert(LDCNext, new LDC(cpgen.addFloat((Float) value))); }
			else if (value instanceof Long) { instructionList.insert(LDCNext, new LDC2_W(cpgen.addLong((Long) value))); }
			constants.clear();
		}
		int after = instructionList.getLength();
		isOptimised = (before == after);

		//Simple dead code removal here
		if (isOptimised) {
			ArrayList<Integer> usedLoad = new ArrayList<>();
			for (InstructionHandle handle : instructionList.getInstructionHandles()) {
				if (handle.getInstruction() instanceof LoadInstruction
						&& !(handle.getInstruction() instanceof ALOAD)) {
					usedLoad.add(((LoadInstruction)handle.getInstruction()).getIndex());
					try {
						instructionList.delete(handle);
					} catch (TargetLostException e) {
						e.printStackTrace();
					}
				}
			}
			for (InstructionHandle handle : instructionList.getInstructionHandles()) {
				if (handle.getInstruction() instanceof StoreInstruction) {
					if (!usedLoad.contains(((StoreInstruction)handle.getInstruction()).getIndex())) {
						try {
							if (handle.getPrev().getInstruction() instanceof LDC
									|| handle.getPrev().getInstruction() instanceof LDC2_W) {
								instructionList.delete(handle.getPrev());
							}
							instructionList.delete(handle);
						} catch (TargetLostException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}

		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		Method newMethod = methodGen.getMethod();
		cgen.replaceMethod(method,newMethod);

		//System.out.println("Constant pool: "+cpgen);
//		System.out.println(method.getName());
//		System.out.println(methodGen.getInstructionList());
	}
	
	public void optimize(){
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();
		Method[] methods = cgen.getMethods();

		for (int i = 0; i < methods.length; i++) {
			optimisation(cgen, cpgen, methods[i]);
			while (!isOptimised) {
				optimisation(cgen,cpgen,cgen.getMethods()[i]);
			}
			isOptimised = false;
			passedIf = false;
		}

//		Method[] newMethods = cgen.getMethods();
//		for (Method method : newMethods) {
//			MethodGen methodGen = new MethodGen(method, cgen.getClassName(), cpgen);
//			System.out.println(cgen.getClassName() + " > " + method.getName());
//			System.out.println(methodGen.getInstructionList());
//		}

		this.optimized = gen.getJavaClass();
		this.optimized = cgen.getJavaClass();
	}

	
	public void write (String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}