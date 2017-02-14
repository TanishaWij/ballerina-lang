/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang;

import org.ballerinalang.util.program.BLangPackages;
import org.ballerinalang.util.program.BLangPrograms;
import org.ballerinalang.util.repository.BLangProgramArchive;
import org.ballerinalang.util.repository.FileSystemPackageRepository;
import org.ballerinalang.util.repository.PackageRepository;
import org.wso2.ballerina.core.model.BLangPackage;
import org.wso2.ballerina.core.model.BLangProgram;
import org.wso2.ballerina.core.model.GlobalScope;
import org.wso2.ballerina.core.model.SymbolName;
import org.wso2.ballerina.core.semantics.SemanticAnalyzer;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @since 0.8.0
 */
public class BLangProgramLoader {
    private boolean disableSemanticAnalyzer = false;

    public BLangProgram loadMain(Path programDirPath, Path sourcePath) {
        programDirPath = BLangPrograms.validateAndResolveProgramDirPath(programDirPath);

        // Get the global scope
        GlobalScope globalScope = BLangPrograms.populateGlobalScope();

        // Creates program scope for this Ballerina program
        BLangProgram bLangProgram = new BLangProgram(globalScope, BLangProgram.Category.MAIN_PROGRAM);
        BLangPackage[] bLangPackages = loadPackages(programDirPath, sourcePath, bLangProgram);
        BLangPackage mainPackage = bLangPackages[0];

        // TODO Find cyclic dependencies
        bLangProgram.setMainPackage(mainPackage);
        bLangProgram.define(new SymbolName(mainPackage.getPackagePath()), mainPackage);


        // Analyze the semantic properties of the Ballerina program
        if (!disableSemanticAnalyzer) {
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(bLangProgram);
            bLangProgram.accept(semanticAnalyzer);
        }

        return bLangProgram;
    }

    public BLangProgram[] loadServices(Path programDirPath, Path[] servicePaths) {
        programDirPath = BLangPrograms.validateAndResolveProgramDirPath(programDirPath);

        // Get the global scope
        GlobalScope globalScope = BLangPrograms.populateGlobalScope();

        BLangProgram[] bLangPrograms = new BLangProgram[servicePaths.length];
        for (int i = 0; i < servicePaths.length; i++) {
            Path servicePath = servicePaths[0];

            // Creates program scope for this Ballerina program
            BLangProgram bLangProgram = new BLangProgram(globalScope, BLangProgram.Category.SERVICE_PROGRAM);
            BLangPackage[] servicePackages = loadPackages(programDirPath, servicePath, bLangProgram);

            // TODO Find cyclic dependencies

            for (BLangPackage servicePkg : servicePackages) {
                bLangProgram.addServicePackage(servicePkg);
                bLangProgram.define(new SymbolName(servicePkg.getPackagePath()), servicePkg);
            }

            // Analyze the semantic properties of the Ballerina program
            if (!disableSemanticAnalyzer) {
                SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(bLangProgram);
                bLangProgram.accept(semanticAnalyzer);
            }

            bLangPrograms[i] = bLangProgram;
        }

        return bLangPrograms;
    }

    public BLangProgramLoader disableSemanticAnalyzer() {
        this.disableSemanticAnalyzer = true;
        return this;
    }

    public BLangProgramLoader addPackageRepository() {
        return this;
    }

    public BLangProgramLoader addErrorListener() {
        // TODO
        return this;
    }

    private BLangPackage[] loadPackages(Path programDirPath,
                                        Path sourcePath,
                                        BLangProgram bLangProgram) {

        sourcePath = BLangPrograms.validateAndResolveSourcePath(programDirPath, sourcePath,
                bLangProgram.getProgramCategory());

        PackageRepository packageRepository = new FileSystemPackageRepository(programDirPath);
        if (Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            Path packagePath = programDirPath.relativize(sourcePath);
            BLangPackage bLangPackage = BLangPackages.loadPackage(packagePath, packageRepository, bLangProgram);
            bLangProgram.addEntryPoint(packagePath.toString());
            return new BLangPackage[]{bLangPackage};

        } else if (sourcePath.toString().endsWith(BLangPrograms.BSOURCE_FILE_EXT)) {
            BLangPackage bLangPackage = BLangPackages.loadFile(sourcePath, packageRepository, bLangProgram);
            bLangProgram.addEntryPoint(sourcePath.getFileName().toString());
            return new BLangPackage[]{bLangPackage};
        } else {
            return loadArchive(sourcePath, bLangProgram);
        }
    }

    private BLangPackage[] loadArchive(Path archivePath, BLangProgram bLangProgram) {
        try (BLangProgramArchive programArchive = new BLangProgramArchive(archivePath)) {

            // Load the program archive
            programArchive.loadArchive();

            String[] entryPoints = programArchive.getEntryPoints();
            if (entryPoints.length > 1 && bLangProgram.getProgramCategory() == BLangProgram.Category.MAIN_PROGRAM) {
                throw new IllegalArgumentException("invalid program archive: " +
                        archivePath + " : multiple entry points");
            }

            List<BLangPackage> bLangPackageList = new ArrayList<>();
            for (String entryPoint : entryPoints) {
                if (entryPoint.endsWith(".bal")) {
                    Path filePath = Paths.get(entryPoint);
                    BLangPackage bLangPackage = BLangPackages.loadFile(filePath, programArchive, bLangProgram);
                    bLangPackageList.add(bLangPackage);
                    bLangProgram.addEntryPoint(filePath.getFileName().toString());
                } else {
                    Path packagePath = Paths.get(entryPoint);
                    BLangPackage bLangPackage = BLangPackages.loadPackage(packagePath, programArchive, bLangProgram);
                    bLangPackageList.add(bLangPackage);
                    bLangProgram.addEntryPoint(packagePath.toString());
                }
            }

            return bLangPackageList.toArray(new BLangPackage[0]);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
