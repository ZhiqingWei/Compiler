package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
	boolean isOptimised = false;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			System.out.println(e.toString());
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
			result = one.intValue() - two.intValue();
		}
		else if (instruction instanceof DSUB) {
			result = one.doubleValue() - two.doubleValue();
		}
		else if (instruction instanceof FSUB) {
			result = one.floatValue() - two.floatValue();
		}
		else if (instruction instanceof LSUB) {
			result = one.longValue() - two.longValue();
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
			System.out.println(constants);
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
			InstructionHandle insertPoint = handle.getNext();
			if (!(instruction instanceof LDC || instruction instanceof LDC2_W)) {
				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
				if (instruction instanceof BIPUSH)
					instructionList.insert(insertPoint, new LDC(cpgen.addInteger((Integer) number)));
				else if (instruction instanceof SIPUSH)
					instructionList.insert(insertPoint, new LDC2_W(cpgen.addInteger((Integer) number)));
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
		int before = instructionList.getLength();

		InstructionHandle LDCNext = null;
		HashMap<Integer,Number> storeValues = new HashMap<>();

		for (InstructionHandle handle: instructionList.getInstructionHandles()) {
			Instruction instruction = handle.getInstruction();

			boolean isLDC = (instruction instanceof LDC || instruction instanceof LDC2_W
							|| instruction instanceof BIPUSH || instruction instanceof SIPUSH);
			boolean isArithmetic = (instruction instanceof ArithmeticInstruction);
			boolean isStore = (instruction instanceof StoreInstruction);
			boolean isConstant = (instruction instanceof ConstantPushInstruction
							&& !(instruction instanceof BIPUSH || instruction instanceof SIPUSH));
			boolean isI2D = (instruction instanceof I2D);
			boolean isLoad = (instruction instanceof LoadInstruction);


			if (isLDC && (handle.getNext().getInstruction() instanceof StoreInstruction)) {
				LDCNext = doLDC(cpgen,instructionList,handle,false);
				System.out.println("After store instruction: " +constants);
			}
			else if (isLDC) {
				LDCNext = doLDC(cpgen,instructionList,handle,true);
			}

			if (isStore) {
				Number value = constants.pop();
				Integer index = ((StoreInstruction)instruction).getIndex();
				storeValues.put(index,value);
				System.out.println("Value to store: " +index+">>>"+value); // pop when store
			}

			if (isConstant && !(handle.getNext().getInstruction() instanceof StoreInstruction)) {
				constants.push(((ConstantPushInstruction)instruction).getValue());
				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}
			else if (isConstant){
				constants.push(((ConstantPushInstruction)instruction).getValue());
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
			}

			if (isI2D) {
				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}

			if (isArithmetic) {
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
					System.out.println("ccc:  " + constants);
				}
			}

			if (isLoad && !(instruction instanceof ALOAD)) {
				int stackIndex = ((LoadInstruction)instruction).getIndex();
				System.out.println(stackIndex);
				constants.push(storeValues.get(stackIndex));
			}
		}

		//System.out.println("constants size: "+constants.size());
		if (constants.size() > 0) {
			Number value = constants.pop();
			if (value instanceof Integer) { instructionList.insert(LDCNext, new LDC(cpgen.addInteger((Integer) value))); }
			else if (value instanceof Double) { instructionList.insert(LDCNext, new LDC2_W(cpgen.addDouble((Double) value))); }
			else if (value instanceof Float) { instructionList.insert(LDCNext, new LDC(cpgen.addFloat((Float) value))); }
			else if (value instanceof Long) { instructionList.insert(LDCNext, new LDC2_W(cpgen.addLong((Long) value))); }
			constants.clear();
		}

		int after = instructionList.getLength();
		isOptimised = (before == after);

		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		Method newMethod = methodGen.getMethod();
		cgen.replaceMethod(method,newMethod);

		//System.out.println("Constant pool: "+cpgen);
		System.out.println(method.getName()+">>>>");
		System.out.println(methodGen.getInstructionList());
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
		}

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
			System.out.println(e.toString());
		} catch (IOException e) {
			// Auto-generated catch block
			System.out.println(e.toString());
		}
	}
}