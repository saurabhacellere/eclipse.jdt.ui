package org.eclipse.jdt.ui.tests.astrewrite;

import java.util.Hashtable;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestPluginLauncher;

import org.eclipse.jdt.internal.corext.dom.ASTRewriteAnalyzer;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;
import org.eclipse.jdt.internal.ui.text.correction.ASTRewriteCorrectionProposal;

public class ASTRewritingStatementsTest extends ASTRewritingTest {

	private final boolean BUG_23259= true;
	
	private static final Class THIS= ASTRewritingStatementsTest.class;
	
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	public ASTRewritingStatementsTest(String name) {
		super(name);
	}

	public static void main(String[] args) {
		TestPluginLauncher.run(TestPluginLauncher.getLocationFromProperties(), THIS, args);
	}


	public static Test suite() {
		if (true) {
			return new TestSuite(THIS);
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new ASTRewritingStatementsTest("testWhileStatement"));
			return suite;
		}
	}


	protected void setUp() throws Exception {
		Hashtable options= JavaCore.getOptions();
		options.put(JavaCore.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(JavaCore.FORMATTER_TAB_SIZE, "4");
		JavaCore.setOptions(options);			
		
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		assertTrue("rt not found", JavaProjectHelper.addRTJar(fJProject1) != null);

		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, "src");
	}

	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJProject1);
	}
	
	public void testAdd() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		{	/* foo(): append a return statement */
			StringBuffer buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class C {\n");
			buf.append("    public Object foo() {\n");
			buf.append("        if (this.equals(new Object())) {\n");
			buf.append("            toString();\n");
			buf.append("        }\n");
			buf.append("    }\n");
			buf.append("}\n");	
			ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
		
			CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
			TypeDeclaration type= findTypeDeclaration(astRoot, "C");
			
			MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
			Block block= methodDecl.getBody();
			assertTrue("No block" , block != null);	
			
			List statements= block.statements();
			ReturnStatement returnStatement= block.getAST().newReturnStatement();
			returnStatement.setExpression(ASTResolving.getNullExpression(methodDecl.getReturnType()));
			statements.add(returnStatement);
			ASTRewriteAnalyzer.markAsInserted(returnStatement);
			
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
			proposal.getCompilationUnitChange().setSave(true);
			
			proposal.apply(null);
			
			buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class C {\n");
			buf.append("    public Object foo() {\n");
			buf.append("        if (this.equals(new Object())) {\n");
			buf.append("            toString();\n");
			buf.append("        }\n");
			buf.append("        return null;\n");
			buf.append("    }\n");
			buf.append("}\n");
			
			assertEqualString(cu.getSource(), buf.toString());
		}
		{	/* hoo(): return; -> return false;  */
			StringBuffer buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class D {\n");
			buf.append("    public Object goo() {\n");
			buf.append("        return new Integer(3);\n");
			buf.append("    }\n");
			buf.append("    public void hoo(int p1, Object p2) {\n");
			buf.append("        return;\n");
			buf.append("    }\n");		
			buf.append("}\n");	
			ICompilationUnit cu= pack1.createCompilationUnit("D.java", buf.toString(), false, null);
			
			CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
			TypeDeclaration type= findTypeDeclaration(astRoot, "D");
		
			MethodDeclaration methodDecl= findMethodDeclaration(type, "hoo");
			Block block= methodDecl.getBody();
			assertTrue("No block" , block != null);
			
			List statements= block.statements();
			assertTrue("No statements in block", !statements.isEmpty());
			assertTrue("No ReturnStatement", statements.get(0) instanceof ReturnStatement);
			
			ReturnStatement returnStatement= (ReturnStatement) statements.get(0);
			Expression expr= block.getAST().newBooleanLiteral(false);
			
			returnStatement.setExpression(expr);
			ASTRewriteAnalyzer.markAsInserted(expr);
			
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
			proposal.getCompilationUnitChange().setSave(true);
			
			proposal.apply(null);
			
			buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class D {\n");
			buf.append("    public Object goo() {\n");
			buf.append("        return new Integer(3);\n");
			buf.append("    }\n");
			buf.append("    public void hoo(int p1, Object p2) {\n");
			buf.append("        return false;\n");
			buf.append("    }\n");		
			buf.append("}\n");	
			
			assertEqualString(cu.getSource(), buf.toString());			
		}
		
	}
	
	public void testRemove() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);			
		{	/* foo():  remove if... */
			StringBuffer buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class C {\n");
			buf.append("    public Object foo() {\n");
			buf.append("        if (this.equals(new Object())) {\n");
			buf.append("            toString();\n");
			buf.append("        }\n");
			buf.append("    }\n");
			buf.append("}\n");	
			ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
			
			CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
			TypeDeclaration type= findTypeDeclaration(astRoot, "C");
			
			MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
			Block block= methodDecl.getBody();
			assertTrue("No block" , block != null);	
			
			List statements= block.statements();
			assertTrue("No statements in block", !statements.isEmpty());
			
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) statements.get(0));
					
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
			proposal.getCompilationUnitChange().setSave(true);
			
			proposal.apply(null);
			
			buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class C {\n");
			buf.append("    public Object foo() {\n");
			buf.append("    }\n");
			buf.append("}\n");
				
			assertEqualString(cu.getSource(), buf.toString());
		}
		{
			StringBuffer buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class D {\n");
			buf.append("    public Object goo() {\n");
			buf.append("        return new Integer(3);\n");
			buf.append("    }\n");
			buf.append("    public void hoo(int p1, Object p2) {\n");
			buf.append("        return;\n");
			buf.append("    }\n");		
			buf.append("}\n");	
			ICompilationUnit cu= pack1.createCompilationUnit("D.java", buf.toString(), false, null);
			
			CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
			TypeDeclaration type= findTypeDeclaration(astRoot, "D");
		
			MethodDeclaration methodDecl= findMethodDeclaration(type, "goo");
			Block block= methodDecl.getBody();
			assertTrue("No block" , block != null);
			
			List statements= block.statements();
			assertTrue("No statements in block", !statements.isEmpty());
			assertTrue("No ReturnStatement", statements.get(0) instanceof ReturnStatement);
			
			ReturnStatement returnStatement= (ReturnStatement) statements.get(0);
			Expression expr= returnStatement.getExpression();
			ASTRewriteAnalyzer.markAsRemoved(expr);
			
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
			proposal.getCompilationUnitChange().setSave(true);
			
			proposal.apply(null);
			
			buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class D {\n");
			buf.append("    public Object goo() {\n");
			buf.append("        return;\n");
			buf.append("    }\n");
			buf.append("    public void hoo(int p1, Object p2) {\n");
			buf.append("        return;\n");
			buf.append("    }\n");		
			buf.append("}\n");	
			
			assertEqualString(cu.getSource(), buf.toString());			
		}
	}
	
	public void testReplace() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);		
		{	/* foo(): if.. -> return; */
			StringBuffer buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class C {\n");
			buf.append("    public Object foo() {\n");
			buf.append("        if (this.equals(new Object())) {\n");
			buf.append("            toString();\n");
			buf.append("        }\n");
			buf.append("    }\n");
			buf.append("}\n");	
			ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
			
			CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
			TypeDeclaration type= findTypeDeclaration(astRoot, "C");
			
			MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
			Block block= methodDecl.getBody();
			assertTrue("No block" , block != null);	
			
			List statements= block.statements();
			assertTrue("No statements in block", !statements.isEmpty());
			
			ReturnStatement returnStatement= block.getAST().newReturnStatement();
			ASTRewriteAnalyzer.markAsReplaced((ASTNode) statements.get(0), returnStatement);
					
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
			proposal.getCompilationUnitChange().setSave(true);
			
			proposal.apply(null);
			
			buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class C {\n");
			buf.append("    public Object foo() {\n");
			buf.append("        return;\n");			
			buf.append("    }\n");
			buf.append("}\n");
				
			assertEqualString(cu.getSource(), buf.toString());
		}		
		{	/* goo(): new Integer(3) -> 'null' */
			StringBuffer buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class D {\n");
			buf.append("    public Object goo() {\n");
			buf.append("        return new Integer(3);\n");
			buf.append("    }\n");
			buf.append("    public void hoo(int p1, Object p2) {\n");
			buf.append("        return;\n");
			buf.append("    }\n");		
			buf.append("}\n");	
			ICompilationUnit cu= pack1.createCompilationUnit("D.java", buf.toString(), false, null);
			
			CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
			TypeDeclaration type= findTypeDeclaration(astRoot, "D");
		
			MethodDeclaration methodDecl= findMethodDeclaration(type, "goo");
			Block block= methodDecl.getBody();
			assertTrue("No block" , block != null);
			
			List statements= block.statements();
			assertTrue("No statements in block", !statements.isEmpty());
			assertTrue("No ReturnStatement", statements.get(0) instanceof ReturnStatement);
			
			ReturnStatement returnStatement= (ReturnStatement) statements.get(0);
			Expression expr= returnStatement.getExpression();
			Expression modified= ASTResolving.getNullExpression(methodDecl.getReturnType());
	
			ASTRewriteAnalyzer.markAsReplaced(expr, modified);
			
			ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
			proposal.getCompilationUnitChange().setSave(true);
			
			proposal.apply(null);
			
			buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class D {\n");
			buf.append("    public Object goo() {\n");
			buf.append("        return null;\n");
			buf.append("    }\n");
			buf.append("    public void hoo(int p1, Object p2) {\n");
			buf.append("        return;\n");
			buf.append("    }\n");		
			buf.append("}\n");	
			
			assertEqualString(cu.getSource(), buf.toString());
		}
	}
	
	public void testBreakStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        break;\n");
		buf.append("        break label;\n");
		buf.append("        break label;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 3", statements.size() == 3);
		{ // insert label
			BreakStatement statement= (BreakStatement) statements.get(0);
			assertTrue("Has label", statement.getLabel() == null);
			
			SimpleName newLabel= ast.newSimpleName("label2");	
			statement.setLabel(newLabel);
			
			ASTRewriteAnalyzer.markAsInserted(newLabel);
		}
		{ // replace label
			BreakStatement statement= (BreakStatement) statements.get(1);
			
			SimpleName label= statement.getLabel();
			assertTrue("Has no label", label != null);
			
			SimpleName newLabel= ast.newSimpleName("label2");	

			ASTRewriteAnalyzer.markAsReplaced(label, newLabel);
		}
		{ // remove label
			BreakStatement statement= (BreakStatement) statements.get(2);
			
			SimpleName label= statement.getLabel();
			assertTrue("Has no label", label != null);
			
			ASTRewriteAnalyzer.markAsRemoved(label);
		}	
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        break label2;\n");
		buf.append("        break label2;\n");
		buf.append("        break;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}
	
	public void testConstructorInvocation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(String e, String f) {\n");
		buf.append("        this();\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this(\"Hello\", true);\n");
		buf.append("    }\n");		
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration[] declarations= type.getMethods();
		assertTrue("Number of statements not 2", declarations.length == 2);			

		{ // add parameters
			Block block= declarations[0].getBody();
			List statements= block.statements();
			assertTrue("Number of statements not 1", statements.size() == 1);
			
			ConstructorInvocation invocation= (ConstructorInvocation) statements.get(0);
	
			List arguments= invocation.arguments();
			
			StringLiteral stringLiteral1= ast.newStringLiteral();
			stringLiteral1.setLiteralValue("Hello");
			arguments.add(stringLiteral1);
			ASTRewriteAnalyzer.markAsInserted(stringLiteral1);
			
			StringLiteral stringLiteral2= ast.newStringLiteral();
			stringLiteral2.setLiteralValue("World");
			arguments.add(stringLiteral2);
			ASTRewriteAnalyzer.markAsInserted(stringLiteral2);
		}
		{ //remove parameters
			Block block= declarations[1].getBody();
			List statements= block.statements();
			assertTrue("Number of statements not 1", statements.size() == 1);			
			ConstructorInvocation invocation= (ConstructorInvocation) statements.get(0);
	
			List arguments= invocation.arguments();
			
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) arguments.get(0));
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) arguments.get(1));
		}		
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(String e, String f) {\n");
		buf.append("        this(\"Hello\", \"World\");\n");
		buf.append("    }\n");
		buf.append("    public E() {\n");
		buf.append("        this();\n");
		buf.append("    }\n");		
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}
	
	public void testContinueStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        continue;\n");
		buf.append("        continue label;\n");
		buf.append("        continue label;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 3", statements.size() == 3);
		{ // insert label
			ContinueStatement statement= (ContinueStatement) statements.get(0);
			assertTrue("Has label", statement.getLabel() == null);
			
			SimpleName newLabel= ast.newSimpleName("label2");	
			statement.setLabel(newLabel);
			
			ASTRewriteAnalyzer.markAsInserted(newLabel);
		}
		{ // replace label
			ContinueStatement statement= (ContinueStatement) statements.get(1);
			
			SimpleName label= statement.getLabel();
			assertTrue("Has no label", label != null);
			
			SimpleName newLabel= ast.newSimpleName("label2");	

			ASTRewriteAnalyzer.markAsReplaced(label, newLabel);
		}
		{ // remove label
			ContinueStatement statement= (ContinueStatement) statements.get(2);
			
			SimpleName label= statement.getLabel();
			assertTrue("Has no label", label != null);
			
			ASTRewriteAnalyzer.markAsRemoved(label);
		}	
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        continue label2;\n");
		buf.append("        continue label2;\n");
		buf.append("        continue;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}
	
	public void testDoStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        do {\n");
		buf.append("            System.beep();\n");
		buf.append("        } while (i == j);\n");		
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 1", statements.size() == 1);

		{ // replace body and expression
			DoStatement doStatement= (DoStatement) statements.get(0);
			
			Block newBody= ast.newBlock();
			
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName("hoo"));
			invocation.arguments().add(ast.newNumberLiteral("11"));
			
			newBody.statements().add(ast.newExpressionStatement(invocation));
			
			ASTRewriteAnalyzer.markAsReplaced(doStatement.getBody(), newBody);

			BooleanLiteral literal= ast.newBooleanLiteral(true);
			ASTRewriteAnalyzer.markAsReplaced(doStatement.getExpression(), literal);
		}
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        do {\n");
		buf.append("            hoo(11);\n");
		buf.append("        } while (true);\n");		
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}		

	public void testExpressionStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        i= 0;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		assertTrue("Parse errors", (block.getFlags() & ASTNode.MALFORMED) == 0);
		
		List statements= block.statements();
		assertTrue("Number of statements not 1", statements.size() == 1);
		{ // replace expression
			ExpressionStatement stmt= (ExpressionStatement) statements.get(0);
			
			Assignment assignment= (Assignment) stmt.getExpression();
			Expression placeholder= (Expression) ASTRewriteAnalyzer.createCopyTarget(assignment);
									
			Assignment newExpression= ast.newAssignment();
			newExpression.setLeftHandSide(ast.newSimpleName("x"));
			newExpression.setRightHandSide(placeholder);
			newExpression.setOperator(Assignment.Operator.ASSIGN);
	
			ASTRewriteAnalyzer.markAsReplaced(stmt.getExpression(), newExpression);
		}
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        x = i= 0;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());

	}
	
	public void testForStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i= 0; i < len; i++) {\n");
		buf.append("        }\n");
		buf.append("        for (i= 0, j= 0; i < len; i++, j++) {\n");
		buf.append("        }\n");
		buf.append("        for (;;) {\n");
		buf.append("        }\n");	
		buf.append("        for (;;) {\n");
		buf.append("        }\n");						
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		AST ast= astRoot.getAST();

		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		assertTrue("Parse errors", (block.getFlags() & ASTNode.MALFORMED) == 0);
		
		List statements= block.statements();
		assertTrue("Number of statements not 4", statements.size() == 4);

		{ // replace initializer, change expression, add updater, replace cody
			ForStatement forStatement= (ForStatement) statements.get(0);
			
			List initializers= forStatement.initializers();
			assertTrue("Number of initializers not 1", initializers.size() == 1);
			
			Assignment assignment= ast.newAssignment();
			assignment.setLeftHandSide(ast.newSimpleName("i"));
			assignment.setOperator(Assignment.Operator.ASSIGN);
			assignment.setRightHandSide(ast.newNumberLiteral("3"));
			
			ASTRewriteAnalyzer.markAsReplaced((ASTNode) initializers.get(0), assignment);
			
			Assignment assignment2= ast.newAssignment();
			assignment2.setLeftHandSide(ast.newSimpleName("j"));
			assignment2.setOperator(Assignment.Operator.ASSIGN);
			assignment2.setRightHandSide(ast.newNumberLiteral("4"));
			
			ASTRewriteAnalyzer.markAsInserted(assignment2);
			
			initializers.add(assignment2);
			
			BooleanLiteral literal= ast.newBooleanLiteral(true);
			ASTRewriteAnalyzer.markAsReplaced(forStatement.getExpression(), literal);
			
			// add updater
			PrefixExpression prefixExpression= ast.newPrefixExpression();
			prefixExpression.setOperand(ast.newSimpleName("j"));
			prefixExpression.setOperator(PrefixExpression.Operator.INCREMENT);
			
			forStatement.updaters().add(prefixExpression);
			
			ASTRewriteAnalyzer.markAsInserted(prefixExpression);
			
			// replace body		
			Block newBody= ast.newBlock();
			
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName("hoo"));
			invocation.arguments().add(ast.newNumberLiteral("11"));
			
			newBody.statements().add(ast.newExpressionStatement(invocation));
			
			ASTRewriteAnalyzer.markAsReplaced(forStatement.getBody(), newBody);
		}
		{ // remove initializers, expression and updaters
			ForStatement forStatement= (ForStatement) statements.get(1);
			
			List initializers= forStatement.initializers();
			assertTrue("Number of initializers not 2", initializers.size() == 2);
			
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) initializers.get(0));
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) initializers.get(1));
			
			ASTRewriteAnalyzer.markAsRemoved(forStatement.getExpression());
			
			List updaters= forStatement.updaters();
			assertTrue("Number of initializers not 2", updaters.size() == 2);
			
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) updaters.get(0));
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) updaters.get(1));
		}
		{ // insert updater
			ForStatement forStatement= (ForStatement) statements.get(2);
			
			PrefixExpression prefixExpression= ast.newPrefixExpression();
			prefixExpression.setOperand(ast.newSimpleName("j"));
			prefixExpression.setOperator(PrefixExpression.Operator.INCREMENT);
			
			forStatement.updaters().add(prefixExpression);
			
			ASTRewriteAnalyzer.markAsInserted(prefixExpression);
		}
		
		{ // insert updater & initializer
			ForStatement forStatement= (ForStatement) statements.get(3);
			
			Assignment assignment= ast.newAssignment();
			assignment.setLeftHandSide(ast.newSimpleName("j"));
			assignment.setOperator(Assignment.Operator.ASSIGN);
			assignment.setRightHandSide(ast.newNumberLiteral("3"));
			
			forStatement.initializers().add(assignment);
			
			ASTRewriteAnalyzer.markAsInserted(assignment);	
			
			PrefixExpression prefixExpression= ast.newPrefixExpression();
			prefixExpression.setOperand(ast.newSimpleName("j"));
			prefixExpression.setOperator(PrefixExpression.Operator.INCREMENT);
			
			forStatement.updaters().add(prefixExpression);
			
			ASTRewriteAnalyzer.markAsInserted(prefixExpression);
		}			
		
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (i = 3, j = 4; true; i++, ++j) {\n");
		buf.append("            hoo(11);\n");
		buf.append("        }\n");
		buf.append("        for (;;) {\n");
		buf.append("        }\n");
		buf.append("        for (;;++j) {\n");
		buf.append("        }\n");		
		buf.append("        for (j = 3;;++j) {\n");
		buf.append("        }\n");		
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}		
	
	public void testIfStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (i == 0) {\n");
		buf.append("            System.beep();\n");
		buf.append("        } else {\n");
		buf.append("        }\n");
		buf.append("        if (i == 0) {\n");
		buf.append("            System.beep();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 2", statements.size() == 2);

		{ // replace expression body and then body, remove else body
			IfStatement ifStatement= (IfStatement) statements.get(0);
			
			BooleanLiteral literal= ast.newBooleanLiteral(true);
			ASTRewriteAnalyzer.markAsReplaced(ifStatement.getExpression(), literal);			
			
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName("hoo"));
			invocation.arguments().add(ast.newNumberLiteral("11"));
			Block newBody= ast.newBlock();
			newBody.statements().add(ast.newExpressionStatement(invocation));
			
			ASTRewriteAnalyzer.markAsReplaced(ifStatement.getThenStatement(), newBody);
			
			ASTRewriteAnalyzer.markAsRemoved(ifStatement.getElseStatement());
		}
		{ // add else body
			IfStatement ifStatement= (IfStatement) statements.get(1);
			
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName("hoo"));
			invocation.arguments().add(ast.newNumberLiteral("11"));
			Block newBody= ast.newBlock();
			newBody.statements().add(ast.newExpressionStatement(invocation));
			
			ASTRewriteAnalyzer.markAsInserted(newBody);
			
			ifStatement.setElseStatement(newBody);
		}		
		
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (true) {\n");
		buf.append("            hoo(11);\n");
		buf.append("        }\n");
		buf.append("        if (i == 0) {\n");
		buf.append("            System.beep();\n");
		buf.append("        } else {\n");
		buf.append("            hoo(11);\n");
		buf.append("        }\n");		
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}
	
	public void testLabeledStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        label: if (i == 0) {\n");
		buf.append("            System.beep();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 1", statements.size() == 1);

		{ // replace label and statement
			LabeledStatement labeledStatement= (LabeledStatement) statements.get(0);
			
			Name newLabel= ast.newSimpleName("newLabel");
			
			ASTRewriteAnalyzer.markAsReplaced(labeledStatement.getLabel(), newLabel);
						
			Assignment newExpression= ast.newAssignment();
			newExpression.setLeftHandSide(ast.newSimpleName("x"));
			newExpression.setRightHandSide(ast.newNumberLiteral("1"));
			newExpression.setOperator(Assignment.Operator.ASSIGN);
			
			Statement newStatement= ast.newExpressionStatement(newExpression);
			
			ASTRewriteAnalyzer.markAsReplaced(labeledStatement.getBody(), newStatement);
		}		
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        newLabel: x = 1;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}		
	
	public void testReturnStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        return;\n");
		buf.append("        return 1;\n");
		buf.append("        return 1;\n");
		buf.append("        return 1 + 2;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 4", statements.size() == 4);
		{ // insert expression
			ReturnStatement statement= (ReturnStatement) statements.get(0);
			assertTrue("Has expression", statement.getExpression() == null);
			
			SimpleName newExpression= ast.newSimpleName("x");	
			statement.setExpression(newExpression);
			
			ASTRewriteAnalyzer.markAsInserted(newExpression);
		}
		{ // replace expression
			ReturnStatement statement= (ReturnStatement) statements.get(1);
			
			Expression expression= statement.getExpression();
			assertTrue("Has no label", expression != null);
			
			SimpleName newExpression= ast.newSimpleName("x");

			ASTRewriteAnalyzer.markAsReplaced(expression, newExpression);
		}
		{ // remove expression
			ReturnStatement statement= (ReturnStatement) statements.get(2);
			
			Expression expression= statement.getExpression();
			assertTrue("Has no label", expression != null);
			
			ASTRewriteAnalyzer.markAsRemoved(expression);
		}
		{ // modify in expression (no change)
			ReturnStatement statement= (ReturnStatement) statements.get(3);
			
			InfixExpression expression= (InfixExpression) statement.getExpression();
			ASTRewriteAnalyzer.markAsReplaced(expression.getLeftOperand(), ast.newNumberLiteral("9"));
		}		
		
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        return x;\n");
		buf.append("        return x;\n");
		buf.append("        return;\n");
		buf.append("        return 9 + 2;\n");		
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}
	
	public void testSwitchStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        switch (i) {\n");
		buf.append("        }\n");
		buf.append("        switch (i) {\n");
		buf.append("            case 1:\n");
		buf.append("                i= 1;\n");
		buf.append("                break;\n");	
		buf.append("            case 2:\n");
		buf.append("                i= 2;\n");
		buf.append("                break;\n");			
		buf.append("            default:\n");
		buf.append("                i= 3;\n");		
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List blockStatements= block.statements();
		assertTrue("Number of statements not 2", blockStatements.size() == 2);
		{ // insert statements, replace expression
			SwitchStatement switchStatement= (SwitchStatement) blockStatements.get(0);
			
			ASTNode expression= switchStatement.getExpression();
			SimpleName newExpression= ast.newSimpleName("x");	
			ASTRewriteAnalyzer.markAsReplaced(expression, newExpression);
			
			List statements= switchStatement.statements();
			assertTrue("Number of statements not 0", statements.size() == 0);
			
			SwitchCase caseStatement1= ast.newSwitchCase();
			caseStatement1.setExpression(ast.newNumberLiteral("1"));
			ASTRewriteAnalyzer.markAsInserted(caseStatement1);
			statements.add(caseStatement1);
			
			Statement statement1= ast.newReturnStatement();
			ASTRewriteAnalyzer.markAsInserted(statement1);
			statements.add(statement1);
			
			SwitchCase caseStatement2= ast.newSwitchCase(); // default
			caseStatement2.setExpression(null);
			ASTRewriteAnalyzer.markAsInserted(caseStatement2);
			statements.add(caseStatement2);
		}
		
		{ // insert, remove, replace statements, change case statements
			SwitchStatement switchStatement= (SwitchStatement) blockStatements.get(1);
			
			List statements= switchStatement.statements();
			assertTrue("Number of statements not 8", statements.size() == 8);
			
			// remove statements
			
			if (BUG_23259) {
				System.out.println(getClass().getName()+"::" + getName() +" limited (bug 23259)");
			} else {
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) statements.get(0));
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) statements.get(1));
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) statements.get(2));
			}
			
			// change case statement
			SwitchCase caseStatement= (SwitchCase) statements.get(3);
			Expression newCaseExpression= ast.newNumberLiteral("10");
			ASTRewriteAnalyzer.markAsReplaced(caseStatement.getExpression(), newCaseExpression);
			
			{
				// insert case statement
				SwitchCase caseStatement2= ast.newSwitchCase();
				caseStatement2.setExpression(ast.newNumberLiteral("11"));
				ASTRewriteAnalyzer.markAsInserted(caseStatement2);
				statements.add(0, caseStatement2);
	
				// insert statement
				Statement statement1= ast.newReturnStatement();
				ASTRewriteAnalyzer.markAsInserted(statement1);
				statements.add(1, statement1);
			}
			
			{
				// insert case statement
				SwitchCase caseStatement2= ast.newSwitchCase();
				caseStatement2.setExpression(ast.newNumberLiteral("12"));
				ASTRewriteAnalyzer.markAsInserted(caseStatement2);
				statements.add(caseStatement2);
	
				// insert statement
				Statement statement1= ast.newReturnStatement();
				ASTRewriteAnalyzer.markAsInserted(statement1);
				statements.add(statement1);
			}			
			

		}		
	
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        switch (x) {\n");
		buf.append("            case 1 :\n");
		buf.append("                return;\n");
		buf.append("            default :\n");			
		buf.append("        }\n");
		buf.append("        switch (i) {\n");
		buf.append("            case 11 :\n");
		buf.append("                return;\n");
		if (BUG_23259) {		
		buf.append("            case 1:\n");
		buf.append("                i= 1;\n");
		buf.append("                break;\n");
		}	
		buf.append("            case 10:\n");		
		buf.append("                i= 2;\n");
		buf.append("                break;\n");			
		buf.append("            default:\n");
		buf.append("                i= 3;\n");
		buf.append("            case 12 :\n");
		buf.append("                return;\n");			
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEqualString(cu.getSource(), buf.toString());
	}

	public void testSynchronizedStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        synchronized(this) {\n");
		buf.append("            System.beep();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 1", statements.size() == 1);

		{ // replace expression and body
			SynchronizedStatement statement= (SynchronizedStatement) statements.get(0);
			ASTNode newExpression= ast.newSimpleName("obj");
			ASTRewriteAnalyzer.markAsReplaced(statement.getExpression(), newExpression);
			
			Block newBody= ast.newBlock();
						
			Assignment assign= ast.newAssignment();
			assign.setLeftHandSide(ast.newSimpleName("x"));
			assign.setRightHandSide(ast.newNumberLiteral("1"));
			assign.setOperator(Assignment.Operator.ASSIGN);
			
			newBody.statements().add(ast.newExpressionStatement(assign));
			
			ASTRewriteAnalyzer.markAsReplaced(statement.getBody(), newBody);
		}		
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        synchronized(obj) {\n");
		buf.append("            x = 1;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}
	
	public void testThrowStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        throw new Exception();\n");
		buf.append("    }\n");
		buf.append("    public void goo() {\n");
		buf.append("        throw new Exception('d');\n");
		buf.append("    }\n");		
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		{ // replace expression
			MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
			Block block= methodDecl.getBody();
			List statements= block.statements();
			assertTrue("Number of statements not 1", statements.size() == 1);			
			
			ThrowStatement statement= (ThrowStatement) statements.get(0);
			
			ClassInstanceCreation creation= ast.newClassInstanceCreation();
			creation.setName(ast.newSimpleName("NullPointerException"));
			creation.arguments().add(ast.newSimpleName("x"));
			
			ASTRewriteAnalyzer.markAsReplaced(statement.getExpression(), creation);
		}
		
		{ // modify expression
			MethodDeclaration methodDecl= findMethodDeclaration(type, "goo");
			Block block= methodDecl.getBody();
			List statements= block.statements();
			assertTrue("Number of statements not 1", statements.size() == 1);			
			
			ThrowStatement statement= (ThrowStatement) statements.get(0);			
			
			ClassInstanceCreation creation= (ClassInstanceCreation) statement.getExpression();
			
			ASTNode newArgument= ast.newSimpleName("x");
			ASTRewriteAnalyzer.markAsReplaced((ASTNode) creation.arguments().get(0), newArgument);
		}				
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        throw new NullPointerException(x);\n");
		buf.append("    }\n");
		buf.append("    public void goo() {\n");
		buf.append("        throw new Exception(x);\n");
		buf.append("    }\n");		
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}		
		
	public void testTryStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        try {\n");	
		buf.append("        } finally {\n");
		buf.append("        }\n");		
		buf.append("        try {\n");	
		buf.append("        } catch (IOException e) {\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");
		buf.append("        try {\n");	
		buf.append("        } catch (IOException e) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List blockStatements= block.statements();
		assertTrue("Number of statements not 3", blockStatements.size() == 3);
		{ // add catch, replace finally
			TryStatement tryStatement= (TryStatement) blockStatements.get(0);
			
			CatchClause catchClause= ast.newCatchClause();
			SingleVariableDeclaration decl= ast.newSingleVariableDeclaration();
			decl.setType(ast.newSimpleType(ast.newSimpleName("IOException")));
			decl.setName(ast.newSimpleName("e"));
			catchClause.setException(decl);
			
			ASTRewriteAnalyzer.markAsInserted(catchClause);
			
			tryStatement.catchClauses().add(catchClause);
			
			Block body= ast.newBlock();
			body.statements().add(ast.newReturnStatement());
			
			ASTRewriteAnalyzer.markAsReplaced(tryStatement.getFinally(), body);
		}
		{ // replace catch, remove finally
			TryStatement tryStatement= (TryStatement) blockStatements.get(1);
			
			List catchClauses= tryStatement.catchClauses();
			
			CatchClause catchClause= ast.newCatchClause();
			SingleVariableDeclaration decl= ast.newSingleVariableDeclaration();
			decl.setType(ast.newSimpleType(ast.newSimpleName("Exception")));
			decl.setName(ast.newSimpleName("x"));
			catchClause.setException(decl);
			
			ASTRewriteAnalyzer.markAsReplaced((ASTNode) catchClauses.get(0), catchClause);
					
			ASTRewriteAnalyzer.markAsRemoved(tryStatement.getFinally());
		}
		{ // remove catch, add finally
			TryStatement tryStatement= (TryStatement) blockStatements.get(2);
			
			List catchClauses= tryStatement.catchClauses();
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) catchClauses.get(0));
			
			
			Block body= ast.newBlock();
			body.statements().add(ast.newReturnStatement());
			ASTRewriteAnalyzer.markAsInserted(body);
			
			tryStatement.setFinally(body);
		}		
			
	
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        try {\n");
		buf.append("        } catch (IOException e) {\n");		
		buf.append("        } finally {\n");
		buf.append("            return;\n");				
		buf.append("        }\n");		
		buf.append("        try {\n");	
		buf.append("        } catch (Exception x) {\n");
		buf.append("        }\n");
		buf.append("        try {\n");	
		buf.append("        } finally {\n");
		buf.append("            return;\n");		
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}

	public void testTypeDeclarationStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        class A {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		assertTrue("Parse errors", (block.getFlags() & ASTNode.MALFORMED) == 0);
		
		List statements= block.statements();
		assertTrue("Number of statements not 1", statements.size() == 1);
		{ // replace expression
			TypeDeclarationStatement stmt= (TypeDeclarationStatement) statements.get(0);
			
			TypeDeclaration newDeclaration= ast.newTypeDeclaration();
			newDeclaration.setName(ast.newSimpleName("X"));
			newDeclaration.setInterface(true);
				
			ASTRewriteAnalyzer.markAsReplaced(stmt.getTypeDeclaration(), newDeclaration);
		}
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        interface X {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());

	}
	
	public void testVariableDeclarationStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i1= 1;\n");
		buf.append("        int i2= 1, k2= 2, n2= 3;\n");
		buf.append("        final int i3= 1, k3= 2, n3= 3;\n");
		buf.append("    }\n");		
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("A.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "A");
		
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		assertTrue("Parse errors", (block.getFlags() & ASTNode.MALFORMED) == 0);
		
		List statements= block.statements();
		assertTrue("Number of statements not 3", statements.size() == 3);
		{	// add modifier, change type, add fragment
			VariableDeclarationStatement decl= (VariableDeclarationStatement) statements.get(0);
			
			// add modifier
			VariableDeclarationStatement modifiedNode= ast.newVariableDeclarationStatement(ast.newVariableDeclarationFragment());
			modifiedNode.setModifiers(Modifier.FINAL);
			
			ASTRewriteAnalyzer.markAsModified(decl, modifiedNode);
			
			PrimitiveType newType= ast.newPrimitiveType(PrimitiveType.BOOLEAN);
			ASTRewriteAnalyzer.markAsReplaced(decl.getType(), newType);
			
			List fragments= decl.fragments();
			
			VariableDeclarationFragment frag=	ast.newVariableDeclarationFragment();
			frag.setName(ast.newSimpleName("k1"));
			frag.setInitializer(null);
			
			ASTRewriteAnalyzer.markAsInserted(frag);
			
			fragments.add(frag);
		}
		{	// add modifiers, remove first two fragments, replace last
			VariableDeclarationStatement decl= (VariableDeclarationStatement) statements.get(1);
			
			// add modifier
			VariableDeclarationStatement modifiedNode= ast.newVariableDeclarationStatement(ast.newVariableDeclarationFragment());
			modifiedNode.setModifiers(Modifier.FINAL);
			
			ASTRewriteAnalyzer.markAsModified(decl, modifiedNode);
			
			List fragments= decl.fragments();
			assertTrue("Number of fragments not 3", fragments.size() == 3);
			
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) fragments.get(0));
			ASTRewriteAnalyzer.markAsRemoved((ASTNode) fragments.get(1));
			
			VariableDeclarationFragment frag=	ast.newVariableDeclarationFragment();
			frag.setName(ast.newSimpleName("k2"));
			frag.setInitializer(null);
			
			ASTRewriteAnalyzer.markAsReplaced((ASTNode) fragments.get(2), frag);
		}
		{	// remove modifiers
			VariableDeclarationStatement decl= (VariableDeclarationStatement) statements.get(2);
			
			// add modifier
			VariableDeclarationStatement modifiedNode= ast.newVariableDeclarationStatement(ast.newVariableDeclarationFragment());
			modifiedNode.setModifiers(0);
			
			ASTRewriteAnalyzer.markAsModified(decl, modifiedNode);
		}
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class A {\n");
		buf.append("    public void foo() {\n");		
		buf.append("        final boolean i1= 1, k1;\n");
		buf.append("        final int k2;\n");
		buf.append("        int i3= 1, k3= 2, n3= 3;\n");
		buf.append("    }\n");		
		buf.append("}\n");	
		
		assertEqualString(cu.getSource(), buf.toString());
	}

	public void testWhileStatement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (i == j) {\n");
		buf.append("            System.beep();\n");
		buf.append("        }\n");		
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		CompilationUnit astRoot= AST.parseCompilationUnit(cu, false);
		
		AST ast= astRoot.getAST();
		
		assertTrue("Parse errors", (astRoot.getFlags() & ASTNode.MALFORMED) == 0);
		TypeDeclaration type= findTypeDeclaration(astRoot, "E");
		MethodDeclaration methodDecl= findMethodDeclaration(type, "foo");
		Block block= methodDecl.getBody();
		List statements= block.statements();
		assertTrue("Number of statements not 1", statements.size() == 1);

		{ // replace expression and body
			WhileStatement whileStatement= (WhileStatement) statements.get(0);

			BooleanLiteral literal= ast.newBooleanLiteral(true);
			ASTRewriteAnalyzer.markAsReplaced(whileStatement.getExpression(), literal);
			
			Block newBody= ast.newBlock();
			
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName("hoo"));
			invocation.arguments().add(ast.newNumberLiteral("11"));
			
			newBody.statements().add(ast.newExpressionStatement(invocation));
			
			ASTRewriteAnalyzer.markAsReplaced(whileStatement.getBody(), newBody);
		}
				
		ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal("", cu, astRoot, 10, null);
		proposal.getCompilationUnitChange().setSave(true);
		
		proposal.apply(null);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (true) {\n");
		buf.append("            hoo(11);\n");
		buf.append("        }\n");		
		buf.append("    }\n");
		buf.append("}\n");	
		assertEqualString(cu.getSource(), buf.toString());
	}		

	
}



