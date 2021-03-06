package com.ccnode.codegenerator.database;

import com.ccnode.codegenerator.dialog.MapperUtil;
import com.ccnode.codegenerator.dialog.dto.mybatis.ColumnAndField;
import com.ccnode.codegenerator.methodnameparser.parsedresult.find.FetchProp;
import com.ccnode.codegenerator.pojo.FieldToColumnRelation;
import com.ccnode.codegenerator.util.GenCodeUtil;
import com.ccnode.codegenerator.util.PsiClassUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @Author bruce.ge
 * @Date 2017/3/6
 * @Description
 */
public class GenClassDialog extends DialogWrapper {

    private Project myProject;

    private List<FetchProp> fetchPropList;

    private Map<String, String> fieldMap;

    private String methodName;

    private String classPackageName;

    private List<FieldInfo> fieldInfoList;

    private List<ColumnAndField> columnAndFields = new ArrayList<>();

    private FieldToColumnRelation relation;

    private JTable myJtable;

    private JTextField classFolderText;


    private String modulePath;


    private JTextField resultMapText;

    private JTextField classNameText;

    private String classQutifiedName;

    private GenClassInfo genClassInfo;


    private FieldToColumnRelation extractFieldToColumnRelation;


    public GenClassDialog(Project project, List<FetchProp> props, Map<String, String> fieldMap, String methodName, FieldToColumnRelation relation, PsiClass srcClass) {
        super(project, true);
        //just need to know the module path.
        this.myProject = project;
        this.fetchPropList = props;
        this.fieldMap = fieldMap;
        this.methodName = methodName;
        this.relation = relation;
        this.fieldInfoList = buildClassInfo(props, fieldMap, relation);
        this.classFolderText = new JTextField(srcClass.getContainingFile().getVirtualFile().getParent().getPath());
        this.classNameText = new JTextField(GenCodeUtil.getUpperStart(methodName + "Result"));
        this.resultMapText = new JTextField(methodName + "Result");
        this.modulePath = PsiClassUtil.getModuleSrcPathOfClass(srcClass);
        this.myJtable = new JTable(extractValue(fieldInfoList), new String[]{"columnName", "fieldName", "fieldType"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };
        myJtable.getTableHeader().setReorderingAllowed(false);
        setTitle("create new Class for the result");

        init();
        //make them to customized field names.
        //use with a jtable.
    }

    private static Object[][] extractValue(List<FieldInfo> fieldInfos) {
        Object[][] values = new Object[fieldInfos.size()][];
        for (int i = 0; i < fieldInfos.size(); i++) {
            values[i] = new String[3];
            values[i][0] = fieldInfos.get(i).getColumnName();
            values[i][1] = fieldInfos.get(i).getFieldName();
            values[i][2] = fieldInfos.get(i).getFieldType();
        }
        return values;
    }

    private List<FieldInfo> buildClassInfo(List<FetchProp> props, Map<String, String> fieldMap, FieldToColumnRelation relation) {
        ArrayList<FieldInfo> fieldInfos = new ArrayList<>();
        for (FetchProp prop : props) {
            if (prop.getFetchFunction() == null) {
                FieldInfo fieldInfo = new FieldInfo();
                String columnName = relation.getPropColumn(prop.getFetchProp());
                fieldInfo.setFieldName(GenCodeUtil.getLowerCamel(columnName));
                fieldInfo.setFieldType(fieldMap.get(prop.getFetchProp()));
                fieldInfo.setColumnName(columnName);
                fieldInfos.add(fieldInfo);
            } else {
                String column = DbUtils.buildSelectFunctionVal(prop);
                FieldInfo fieldInfo = new FieldInfo();
                fieldInfo.setColumnName(column);
                fieldInfo.setFieldName(column);
                fieldInfo.setFieldType(DbUtils.getReturnClassFromFunction(fieldMap, prop.getFetchFunction(), prop.getFetchProp()));
                fieldInfos.add(fieldInfo);
            }
        }
        return fieldInfos;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new GridBagLayout());
        GridBagConstraints bag = new GridBagConstraints();
        int mygridy = 0;
        bag.anchor = GridBagConstraints.NORTHWEST;
        bag.fill = GridBagConstraints.BOTH;
        bag.gridwidth = 1;
        bag.weightx = 1;
        bag.weighty = 1;
        bag.gridy = mygridy++;
        bag.gridx = 0;
        jPanel.add(new JLabel("className"), bag);
        bag.gridx = 1;
        jPanel.add(classNameText, bag);
        bag.gridy = 1;
        bag.gridx = 0;
        jPanel.add(new JLabel("resultMapId:"), bag);

        bag.gridx = 1;
        jPanel.add(resultMapText, bag);

        bag.gridy = 3;
        bag.gridx = 0;
        jPanel.add(new JLabel(" class location:"), bag);
        bag.gridx = 1;
        jPanel.add(classFolderText, bag);

        bag.gridy = 4;
        bag.gridx = 1;
        JScrollPane jScrollPane = new JScrollPane(myJtable);
        jPanel.add(jScrollPane, bag);
        return jPanel;
    }

    @Override
    protected void doOKAction() {
        //generate the info for it.
        //gonna save the result.
        String className = classNameText.getText();
        if(StringUtils.isBlank(className)){
            Messages.showErrorDialog(myProject,"the className is empty, please reinput","validefail");
            return;
        }
        String folder = classFolderText.getText();

        String packageToModule = PsiClassUtil.getPackageToModule(folder, modulePath);

        int rowCount = myJtable.getRowCount();

        Set<String> importList = Sets.newHashSet();

        GenClassInfo info = new GenClassInfo();
        List<NewClassFieldInfo> fieldInfos = Lists.newArrayList();
        extractFieldToColumnRelation = new FieldToColumnRelation();
        extractFieldToColumnRelation.setResultMapId(resultMapText.getText());
        Map<String,String> fieldToColumnMap = new LinkedHashMap<>();
        extractFieldToColumnRelation.setFiledToColumnMap(fieldToColumnMap);
        for (int i = 0; i < rowCount; i++) {
            String columnName = (String) myJtable.getValueAt(i, 0);
            String fieldName = (String) myJtable.getValueAt(i, 1);
            String fieldType = (String) myJtable.getValueAt(i, 2);
            fieldToColumnMap.put(fieldName,columnName);
            NewClassFieldInfo e = new NewClassFieldInfo();
            e.setFieldName(fieldName);
            e.setFieldShortType(MapperUtil.extractClassShortName(fieldType));
            String s = MapperUtil.extractPackage(fieldType);
            if (checkIsNeedImport(s)) {
                importList.add(s);
            }
            fieldInfos.add(e);
        }
        info.setClassFullPath(folder + "/" + className + ".java");
        info.setClassFieldInfos(fieldInfos);
        info.setClassName(className);
        info.setClassPackageName(packageToModule);
        info.setImportList(importList);
        this.genClassInfo = info;
        //need refresh file tree.
        this.classQutifiedName = packageToModule + "." + className;
        super.doOKAction();
        //make it happen.
    }

    private boolean checkIsNeedImport(String s) {
        if (s != null && !s.startsWith("java.lang")) {
            return true;
        }
        return false;
    }


    public List<ColumnAndField> getColumnAndFields() {
        return columnAndFields;
    }

    public String getClassQutifiedName() {
        return classQutifiedName;
    }

    public FieldToColumnRelation getExtractFieldToColumnRelation() {
        return extractFieldToColumnRelation;
    }

    public GenClassInfo getGenClassInfo() {
        return genClassInfo;
    }
}
