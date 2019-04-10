package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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

	private void arithematic(Instruction instruction) {
		Number one = constants.pop();
		Number two = constants.pop();
		Number result = null;

		if (instruction instanceof IADD) {
			result = one.intValue() + two.intValue();
		}
		/*else if (instruction instanceof DADD) {
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
		}*/

		if (result != null) constants.push(result);
	}

	private void optimisation(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
		InstructionList instructionList = new InstructionList(method.getCode().getCode());
		MethodGen methodGen = new MethodGen(method.getAccessFlags(),method.getReturnType(),
				method.getArgumentTypes(),null,method.getName(),
				cgen.getClassName(),instructionList,cpgen);
		int before = instructionList.getLength();
		InstructionHandle LDCNext = null;

		System.out.println(before);
		for (InstructionHandle handle: instructionList.getInstructionHandles()) {
			Instruction instruction = handle.getInstruction();
			Number number = null;
			boolean isConstant = false;


			if (instruction instanceof LDC) {
				number = (Number) ((LDC) instruction).getValue(cpgen);
				isConstant = true;
				try {
					LDCNext = handle.getNext();
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}
//			else if (instruction instanceof LDC2_W) {
//				number = (Number) ((LDC2_W) instruction).getValue(cpgen);
//				isConstant = true;
//				instructionList.delete(handle);
//			}
//			else if (instruction instanceof BIPUSH) {
//				number = (Number) ((BIPUSH) instruction).getValue();
//				isConstant = true;
//				instructionList.delete(handle);
//			}
//			else if (instruction instanceof SIPUSH) {
//				number = (Number) ((SIPUSH) instruction).getValue();
//				isConstant = true;
//				instructionList.delete(handle);
//			}

			if (isConstant) {
				constants.push(number);
				System.out.println(constants);
			}


			if (instruction instanceof ArithmeticInstruction && constants.size() >= 2) {
				arithematic(instruction);
				InstructionHandle insertPoint = handle.getNext();
				try {
					instructionList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
				instructionList.insert(insertPoint,new LDC(cpgen.addInteger((int)constants.pop())));
				System.out.println("ccc:  "+constants);
			}
		}

		if (constants.size() == 1) {
			instructionList.insert(LDCNext,new LDC(cpgen.addInteger((int)constants.pop())));
		}

		int after = instructionList.getLength();
		isOptimised = (before == after);

		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		Method newMethod = methodGen.getMethod();
		cgen.replaceMethod(method,newMethod);
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