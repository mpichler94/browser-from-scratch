package io.github.mpichler94.browser

import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class Task : Runnable {
    override fun run() {
        TODO("Not yet implemented")
    }
}

@OptIn(ExperimentalAtomicApi::class)
class TaskRunner {
    private val tasks = LinkedBlockingQueue<Runnable>()
    private val mainThread = Thread.ofVirtual().name("Main thread").unstarted { run() }
    private val needsQuit = AtomicBoolean(false)

    fun start() {
        mainThread.start()
    }

    fun needsQuit() {
        needsQuit.store(true)
        mainThread.interrupt()
    }

    fun schedule(task: Runnable) {
        tasks.offer(task)
    }

    fun clearPendingTasks() {
        tasks.clear()
    }

    private fun run() {
        while (true) {
            if (needsQuit.load()) {
                return
            }
            val task = tasks.take()
            task.run()
        }
    }
}
