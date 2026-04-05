package com.troubashare.data.sync

import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.domain.model.Annotation
import com.troubashare.domain.model.AnnotationScope
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter

/**
 * Exports annotations to the JSONL sync format described in DESIGN.md.
 *
 * File layout:
 *   annotations/fileId_memberId_personal.jsonl
 *   annotations/fileId_partId_part.jsonl
 *   annotations/fileId_all.jsonl
 *
 * Each line is one stroke:
 *   {"id":"uuid","page":0,"tool":"PEN","color":"#FF000000","width":5.0,"opacity":1.0,"pts":"x1,y1;x2,y2","v":1,"by":"deviceId","at":ts}
 */
class JsonlAnnotationExporter(
    private val annotationRepository: AnnotationRepository,
    private val deviceManager: DeviceManager
) {

    suspend fun exportForFile(
        fileId: String,
        memberId: String,
        partIds: List<String>,
        outputDir: File
    ) {
        val annotationsDir = File(outputDir, "annotations").also { it.mkdirs() }

        val allVisible = annotationRepository.getVisibleAnnotationsOnce(fileId, memberId, partIds)

        // Personal annotations
        val personal = allVisible.filter {
            it.scope == AnnotationScope.PERSONAL && it.memberId == memberId
        }
        if (personal.isNotEmpty()) {
            writeAnnotationsToJsonl(personal, File(annotationsDir, "${fileId}_${memberId}_personal.jsonl"))
        }

        // ALL-scoped annotations authored by this member
        val allScoped = allVisible.filter {
            it.scope == AnnotationScope.ALL && it.memberId == memberId
        }
        if (allScoped.isNotEmpty()) {
            writeAnnotationsToJsonl(allScoped, File(annotationsDir, "${fileId}_all.jsonl"))
        }

        // PART-scoped annotations authored by this member
        for (partId in partIds) {
            val partScoped = allVisible.filter {
                it.scope == AnnotationScope.PART && it.partId == partId && it.memberId == memberId
            }
            if (partScoped.isNotEmpty()) {
                writeAnnotationsToJsonl(partScoped, File(annotationsDir, "${fileId}_${partId}_part.jsonl"))
            }
        }
    }

    private fun writeAnnotationsToJsonl(annotations: List<Annotation>, file: File) {
        PrintWriter(file).use { writer ->
            for (annotation in annotations) {
                for (stroke in annotation.strokes) {
                    val pts = stroke.points.joinToString(";") { "${it.x},${it.y}" }
                    val color = "#%08X".format(stroke.color and 0xFFFFFFFFL)
                    val obj = JSONObject()
                    obj.put("id", stroke.id)
                    obj.put("page", annotation.pageNumber)
                    obj.put("tool", stroke.tool.name)
                    obj.put("color", color)
                    obj.put("width", stroke.strokeWidth)
                    obj.put("opacity", stroke.opacity)
                    obj.put("pts", pts)
                    if (stroke.text != null) obj.put("text", stroke.text)
                    obj.put("v", 1)
                    obj.put("by", deviceManager.deviceId)
                    obj.put("at", stroke.createdAt)
                    writer.println(obj.toString())
                }
            }
        }
    }
}
