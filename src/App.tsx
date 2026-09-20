import { useEffect, useState } from 'react'
import {
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  ChevronRight,
  Database,
  Download,
  Files,
  FolderOpen,
  Library,
  LoaderCircle,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  api,
  type Book,
  type DuplicateGroup,
  type Folder,
  type Scan,
  type Stats,
} from '@/lib/api'
import { cn } from '@/lib/utils'

type View = 'overview' | 'library' | 'duplicates'
type DuplicateKind = 'exact' | 'possible'

const EMPTY_STATS: Stats = {
  total: 0,
  pdf: 0,
  epub: 0,
  folders: 0,
  exact_duplicate_groups: 0,
  possible_duplicate_groups: 0,
}

function formatBytes(value: number) {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const unit = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** unit).toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

function formatDate(value: string | null) {
  if (!value) return 'Not scanned yet'
  const normalized = value.includes('T') ? value : `${value.replace(' ', 'T')}Z`
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(normalized))
}

function fileCountLabel(count: number) {
  return `${count.toLocaleString()} ${count === 1 ? 'book' : 'books'}`
}

function App() {
  const [view, setView] = useState<View>('overview')
  const [stats, setStats] = useState<Stats>(EMPTY_STATS)
  const [folders, setFolders] = useState<Folder[]>([])
  const [scans, setScans] = useState<Scan[]>([])
  const [books, setBooks] = useState<Book[]>([])
  const [bookTotal, setBookTotal] = useState(0)
  const [search, setSearch] = useState('')
  const [fileType, setFileType] = useState<'all' | 'pdf' | 'epub'>('all')
  const [page, setPage] = useState(0)
  const [duplicateKind, setDuplicateKind] = useState<DuplicateKind>('exact')
  const [duplicateGroups, setDuplicateGroups] = useState<DuplicateGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [booksLoading, setBooksLoading] = useState(false)
  const [duplicatesLoading, setDuplicatesLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [folderDialogOpen, setFolderDialogOpen] = useState(false)
  const [folderPath, setFolderPath] = useState('')
  const [folderSubmitting, setFolderSubmitting] = useState(false)

  async function refreshCore() {
    const [nextStats, nextFolders, nextScans] = await Promise.all([
      api.stats(),
      api.folders(),
      api.scans(),
    ])
    setStats(nextStats)
    setFolders(nextFolders)
    setScans(nextScans)
  }

  async function refreshBooks(query = search, type = fileType, selectedPage = page) {
    setBooksLoading(true)
    try {
      const response = await api.books(query, type, selectedPage * 100, 100)
      setBooks(response.items)
      setBookTotal(response.total)
    } finally {
      setBooksLoading(false)
    }
  }

  async function refreshDuplicates(kind = duplicateKind) {
    setDuplicatesLoading(true)
    try {
      const response = await api.duplicates(kind)
      setDuplicateGroups(response.groups)
    } finally {
      setDuplicatesLoading(false)
    }
  }

  useEffect(() => {
    async function load() {
      try {
        await Promise.all([refreshCore(), refreshBooks()])
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : 'Athena could not load')
      } finally {
        setLoading(false)
      }
    }
    void load()
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void refreshBooks(search, fileType, page).catch((searchError) => {
        setError(searchError instanceof Error ? searchError.message : 'Search failed')
      })
    }, 250)
    return () => window.clearTimeout(timer)
  }, [search, fileType, page])

  useEffect(() => {
    if (view !== 'duplicates') return
    void refreshDuplicates(duplicateKind).catch((duplicateError) => {
      setError(
        duplicateError instanceof Error ? duplicateError.message : 'Duplicates could not load',
      )
    })
  }, [view, duplicateKind])

  const hasActiveScan = folders.some((folder) => folder.is_scanning)
  useEffect(() => {
    if (!hasActiveScan) return
    const timer = window.setInterval(() => {
      void refreshCore()
        .then(() => Promise.all([refreshBooks(), refreshDuplicates()]))
        .catch(() => undefined)
    }, 1600)
    return () => window.clearInterval(timer)
  }, [hasActiveScan])

  async function addFolder() {
    if (!folderPath.trim()) return
    setFolderSubmitting(true)
    setError(null)
    try {
      await api.addFolder(folderPath.trim())
      setFolderPath('')
      setFolderDialogOpen(false)
      await refreshCore()
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Folder could not be added')
    } finally {
      setFolderSubmitting(false)
    }
  }

  async function rescan(folderId: number) {
    setError(null)
    try {
      await api.rescan(folderId)
      await refreshCore()
    } catch (scanError) {
      setError(scanError instanceof Error ? scanError.message : 'Scan could not start')
    }
  }

  const pageCopy = {
    overview: {
      eyebrow: 'Local library',
      title: 'Your ebook collection, clearly organized',
      description: 'Catalog PDF and EPUB files without changing your original folders.',
    },
    library: {
      eyebrow: `${bookTotal.toLocaleString()} indexed`,
      title: 'Library',
      description: 'Find a book by its title or author.',
    },
    duplicates: {
      eyebrow: 'Review only',
      title: 'Duplicate check',
      description: 'Compare exact copies and books that may be alternate editions.',
    },
  }[view]

  return (
    <div className="min-h-screen bg-[#f7f7f4] text-slate-950">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col bg-[#12231f] px-5 py-6 text-white lg:flex">
        <button
          type="button"
          onClick={() => setView('overview')}
          className="flex items-center gap-3 text-left"
        >
          <span className="grid size-10 place-items-center rounded-xl bg-[#d6f06f] font-serif text-xl font-bold text-[#12231f]">
            A
          </span>
          <span>
            <span className="block text-base font-semibold tracking-tight">Project Athena</span>
            <span className="block text-xs text-emerald-100/60">Private ebook catalog</span>
          </span>
        </button>

        <nav className="mt-10 space-y-1">
          <NavItem
            active={view === 'overview'}
            icon={Database}
            label="Overview"
            onClick={() => setView('overview')}
          />
          <NavItem
            active={view === 'library'}
            icon={Library}
            label="Library"
            count={stats.total}
            onClick={() => setView('library')}
          />
          <NavItem
            active={view === 'duplicates'}
            icon={Files}
            label="Duplicates"
            count={stats.exact_duplicate_groups + stats.possible_duplicate_groups}
            onClick={() => setView('duplicates')}
          />
        </nav>

        <div className="mt-auto rounded-2xl border border-white/10 bg-white/5 p-4">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium">
            <ShieldCheck className="size-4 text-[#d6f06f]" />
            Local and read-only
          </div>
          <p className="text-xs leading-5 text-emerald-50/60">
            Athena catalogs your files in place. It never deletes or uploads your ebooks.
          </p>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 border-b border-slate-200/80 bg-[#f7f7f4]/90 backdrop-blur">
          <div className="flex h-16 items-center justify-between px-4 sm:px-8 lg:px-10">
            <button
              type="button"
              onClick={() => setView('overview')}
              className="flex items-center gap-2 font-semibold lg:hidden"
            >
              <span className="grid size-8 place-items-center rounded-lg bg-[#12231f] text-[#d6f06f]">
                A
              </span>
              Athena
            </button>
            <div className="hidden items-center gap-2 text-xs font-medium text-slate-500 lg:flex">
              <span className="size-2 rounded-full bg-emerald-500" />
              Running locally
            </div>
            <div className="flex items-center gap-2">
              <a
                href="/api/downloads/android"
                download
                className={buttonVariants({
                  variant: 'outline',
                  className: 'rounded-full bg-white px-4',
                })}
              >
                <Download className="size-4" />
                <span className="hidden sm:inline">Download Android APK</span>
                <span className="sm:hidden">APK</span>
              </a>
              <Button
                onClick={() => setFolderDialogOpen(true)}
                className="rounded-full bg-[#12231f] px-4 text-white hover:bg-[#1b352f]"
              >
                <Plus className="size-4" />
                <span className="hidden sm:inline">Add folder</span>
                <span className="sm:hidden">Folder</span>
              </Button>
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-4 pb-3 lg:hidden">
            {(['overview', 'library', 'duplicates'] as View[]).map((item) => (
              <Button
                key={item}
                variant={view === item ? 'secondary' : 'ghost'}
                size="sm"
                onClick={() => setView(item)}
                className="capitalize"
              >
                {item}
              </Button>
            ))}
          </nav>
        </header>

        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-8 lg:px-10 lg:py-10">
          <div className="mb-8 max-w-3xl">
            <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em] text-emerald-700">
              {pageCopy.eyebrow}
            </p>
            <h1 className="text-3xl font-semibold tracking-[-0.035em] text-slate-950 sm:text-4xl">
              {pageCopy.title}
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600 sm:text-base">
              {pageCopy.description}
            </p>
          </div>

          {error && (
            <Alert className="mb-6 border-red-200 bg-red-50 text-red-900">
              <AlertTriangle className="size-4" />
              <AlertDescription className="flex items-center justify-between gap-4">
                <span>{error}</span>
                <button type="button" onClick={() => setError(null)} className="font-medium">
                  Dismiss
                </button>
              </AlertDescription>
            </Alert>
          )}

          {loading ? (
            <LoadingPage />
          ) : view === 'overview' ? (
            <Overview
              stats={stats}
              folders={folders}
              scans={scans}
              onAddFolder={() => setFolderDialogOpen(true)}
              onRescan={rescan}
              onOpenLibrary={() => setView('library')}
              onOpenDuplicates={() => setView('duplicates')}
            />
          ) : view === 'library' ? (
            <LibraryView
              books={books}
              total={bookTotal}
              search={search}
              fileType={fileType}
              page={page}
              loading={booksLoading}
              hasFolders={folders.length > 0}
              onSearch={(value) => {
                setSearch(value)
                setPage(0)
              }}
              onFileType={(value) => {
                setFileType(value)
                setPage(0)
              }}
              onPage={setPage}
              onAddFolder={() => setFolderDialogOpen(true)}
            />
          ) : (
            <DuplicatesView
              kind={duplicateKind}
              groups={duplicateGroups}
              stats={stats}
              loading={duplicatesLoading}
              onKind={setDuplicateKind}
              onOpenLibrary={() => setView('library')}
            />
          )}
        </main>
      </div>

      <Dialog open={folderDialogOpen} onOpenChange={setFolderDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Add an ebook folder</DialogTitle>
            <DialogDescription>
              Enter the full Windows path. Athena scans PDF and EPUB files inside all
              subfolders.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-2">
            <label htmlFor="folder-path" className="text-sm font-medium">
              Folder path
            </label>
            <Input
              id="folder-path"
              value={folderPath}
              onChange={(event) => setFolderPath(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void addFolder()
              }}
              placeholder="C:\Users\YourName\Documents\Ebooks"
              autoFocus
            />
            <p className="text-xs text-slate-500">
              Your source files stay exactly where they are.
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFolderDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => void addFolder()}
              disabled={!folderPath.trim() || folderSubmitting}
              className="bg-[#12231f] text-white hover:bg-[#1b352f]"
            >
              {folderSubmitting && <LoaderCircle className="size-4 animate-spin" />}
              Add and scan
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function NavItem({
  active,
  icon: Icon,
  label,
  count,
  onClick,
}: {
  active: boolean
  icon: typeof Library
  label: string
  count?: number
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors',
        active
          ? 'bg-white/10 font-medium text-white'
          : 'text-emerald-50/65 hover:bg-white/5 hover:text-white',
      )}
    >
      <Icon className="size-[18px]" />
      <span>{label}</span>
      {typeof count === 'number' && (
        <span className="ml-auto text-xs tabular-nums text-emerald-50/45">
          {count.toLocaleString()}
        </span>
      )}
    </button>
  )
}

function Overview({
  stats,
  folders,
  scans,
  onAddFolder,
  onRescan,
  onOpenLibrary,
  onOpenDuplicates,
}: {
  stats: Stats
  folders: Folder[]
  scans: Scan[]
  onAddFolder: () => void
  onRescan: (folderId: number) => Promise<void>
  onOpenLibrary: () => void
  onOpenDuplicates: () => void
}) {
  if (folders.length === 0) {
    return (
      <Card className="overflow-hidden border-0 bg-[#12231f] text-white shadow-none">
        <CardContent className="grid gap-8 p-8 sm:p-10 lg:grid-cols-[1fr_auto] lg:items-end">
          <div>
            <span className="mb-5 grid size-12 place-items-center rounded-2xl bg-white/10">
              <FolderOpen className="size-6 text-[#d6f06f]" />
            </span>
            <h2 className="max-w-xl text-2xl font-semibold tracking-tight sm:text-3xl">
              Start with the folder that holds your ebook collection.
            </h2>
            <p className="mt-3 max-w-xl text-sm leading-6 text-emerald-50/65">
              Athena will build a private catalog from PDF and EPUB metadata. It will not
              rename, move, or upload any file.
            </p>
          </div>
          <Button
            onClick={onAddFolder}
            size="lg"
            className="rounded-full bg-[#d6f06f] text-[#12231f] hover:bg-[#e0f68c]"
          >
            <Plus className="size-4" />
            Choose a folder
          </Button>
        </CardContent>
      </Card>
    )
  }

  const statCards = [
    { label: 'Total books', value: stats.total, detail: 'PDF and EPUB', icon: BookOpen },
    { label: 'PDF files', value: stats.pdf, detail: 'In your catalog', icon: Files },
    { label: 'EPUB files', value: stats.epub, detail: 'In your catalog', icon: Library },
    {
      label: 'Duplicate groups',
      value: stats.exact_duplicate_groups + stats.possible_duplicate_groups,
      detail: `${stats.exact_duplicate_groups} exact`,
      icon: Database,
    },
  ]

  return (
    <div className="space-y-7">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {statCards.map((item) => (
          <Card key={item.label} className="border-slate-200/80 bg-white shadow-none">
            <CardContent className="p-5">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm text-slate-500">{item.label}</p>
                  <p className="mt-2 text-3xl font-semibold tracking-tight tabular-nums">
                    {item.value.toLocaleString()}
                  </p>
                </div>
                <span className="grid size-10 place-items-center rounded-xl bg-[#eef4df] text-emerald-800">
                  <item.icon className="size-5" />
                </span>
              </div>
              <p className="mt-4 text-xs text-slate-500">{item.detail}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.6fr_1fr]">
        <Card className="border-slate-200/80 bg-white shadow-none">
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle>Library folders</CardTitle>
              <p className="mt-1 text-sm text-slate-500">Scanned in place on this laptop</p>
            </div>
            <Button variant="outline" size="sm" onClick={onAddFolder}>
              <Plus className="size-4" />
              Add
            </Button>
          </CardHeader>
          <CardContent className="space-y-3">
            {folders.map((folder) => (
              <div
                key={folder.id}
                className="flex flex-col gap-3 rounded-xl border border-slate-200 p-4 sm:flex-row sm:items-center"
              >
                <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-600">
                  <FolderOpen className="size-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium" title={folder.path}>
                    {folder.path}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {folder.is_scanning
                      ? 'Scanning now…'
                      : `${fileCountLabel(folder.file_count)} · ${formatDate(folder.last_scan_at)}`}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => void onRescan(folder.id)}
                  disabled={folder.is_scanning}
                  aria-label={`Rescan ${folder.path}`}
                >
                  <RefreshCw
                    className={cn('size-4', folder.is_scanning && 'animate-spin')}
                  />
                  {folder.is_scanning ? 'Scanning' : 'Rescan'}
                </Button>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="border-slate-200/80 bg-white shadow-none">
          <CardHeader>
            <CardTitle>Quick actions</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <QuickAction
              icon={Search}
              label="Search your library"
              detail="By title or author"
              onClick={onOpenLibrary}
            />
            <QuickAction
              icon={Files}
              label="Review duplicates"
              detail={`${stats.exact_duplicate_groups + stats.possible_duplicate_groups} groups found`}
              onClick={onOpenDuplicates}
            />
          </CardContent>
        </Card>
      </div>

      {scans[0] && (
        <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
          {scans[0].status === 'running' ? (
            <LoaderCircle className="size-4 animate-spin text-emerald-700" />
          ) : scans[0].status === 'completed' ? (
            <CheckCircle2 className="size-4 text-emerald-700" />
          ) : (
            <AlertTriangle className="size-4 text-amber-600" />
          )}
          <span className="text-slate-600">
            Latest scan: <span className="font-medium text-slate-900">{scans[0].status}</span>
            {' · '}
            {scans[0].indexed.toLocaleString()} indexed
            {scans[0].errors > 0 && ` · ${scans[0].errors} errors`}
          </span>
        </div>
      )}
    </div>
  )
}

function QuickAction({
  icon: Icon,
  label,
  detail,
  onClick,
}: {
  icon: typeof Search
  label: string
  detail: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-xl p-3 text-left transition-colors hover:bg-slate-50"
    >
      <span className="grid size-9 place-items-center rounded-lg bg-slate-100 text-slate-700">
        <Icon className="size-4" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium">{label}</span>
        <span className="block text-xs text-slate-500">{detail}</span>
      </span>
      <ChevronRight className="size-4 text-slate-400" />
    </button>
  )
}

function LibraryView({
  books,
  total,
  search,
  fileType,
  page,
  loading,
  hasFolders,
  onSearch,
  onFileType,
  onPage,
  onAddFolder,
}: {
  books: Book[]
  total: number
  search: string
  fileType: 'all' | 'pdf' | 'epub'
  page: number
  loading: boolean
  hasFolders: boolean
  onSearch: (value: string) => void
  onFileType: (value: 'all' | 'pdf' | 'epub') => void
  onPage: (value: number) => void
  onAddFolder: () => void
}) {
  return (
    <Card className="overflow-hidden border-slate-200/80 bg-white shadow-none">
      <div className="flex flex-col gap-3 border-b border-slate-200 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full max-w-md">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            value={search}
            onChange={(event) => onSearch(event.target.value)}
            placeholder="Search title or author…"
            className="pl-9"
            aria-label="Search books by title or author"
          />
        </div>
        <div className="flex items-center gap-1 rounded-lg bg-slate-100 p-1">
          {(['all', 'pdf', 'epub'] as const).map((type) => (
            <button
              key={type}
              type="button"
              onClick={() => onFileType(type)}
              className={cn(
                'rounded-md px-3 py-1.5 text-xs font-medium uppercase transition-colors',
                fileType === type
                  ? 'bg-white text-slate-950 shadow-sm'
                  : 'text-slate-500 hover:text-slate-800',
              )}
            >
              {type}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="space-y-3 p-5">
          {[1, 2, 3, 4].map((item) => (
            <Skeleton key={item} className="h-12 w-full" />
          ))}
        </div>
      ) : books.length === 0 ? (
        <EmptyState
          icon={hasFolders ? Search : FolderOpen}
          title={hasFolders ? 'No books match your search' : 'Add your first library folder'}
          description={
            hasFolders
              ? 'Try a different title, author, or file type.'
              : 'Athena will catalog PDF and EPUB files without moving them.'
          }
          action={!hasFolders ? { label: 'Add folder', onClick: onAddFolder } : undefined}
        />
      ) : (
        <>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/80">
                  <TableHead className="w-[42%]">Title</TableHead>
                  <TableHead>Author</TableHead>
                  <TableHead>Format</TableHead>
                  <TableHead className="text-right">Size</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {books.map((book) => (
                  <TableRow key={book.id}>
                    <TableCell>
                      <div className="flex items-start gap-3">
                        <span
                          className={cn(
                            'mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg text-[10px] font-bold uppercase',
                            book.file_type === 'pdf'
                              ? 'bg-rose-50 text-rose-700'
                              : 'bg-sky-50 text-sky-700',
                          )}
                        >
                          {book.file_type}
                        </span>
                        <div className="min-w-0">
                          <p className="truncate font-medium" title={book.title}>
                            {book.title}
                          </p>
                          <p className="mt-0.5 max-w-md truncate text-xs text-slate-500" title={book.path}>
                            {book.path}
                          </p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-slate-600">
                      {book.author || <span className="text-slate-400">Unknown author</span>}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary" className="uppercase">
                        {book.file_type}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right text-slate-500">
                      {formatBytes(book.size_bytes)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="flex items-center justify-between gap-4 border-t border-slate-200 px-5 py-3 text-xs text-slate-500">
            <span>
              Showing {(page * 100 + 1).toLocaleString()}–
              {(page * 100 + books.length).toLocaleString()} of {total.toLocaleString()} books
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPage(page - 1)}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPage(page + 1)}
                disabled={(page + 1) * 100 >= total}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </Card>
  )
}

function DuplicatesView({
  kind,
  groups,
  stats,
  loading,
  onKind,
  onOpenLibrary,
}: {
  kind: DuplicateKind
  groups: DuplicateGroup[]
  stats: Stats
  loading: boolean
  onKind: (value: DuplicateKind) => void
  onOpenLibrary: () => void
}) {
  return (
    <div className="space-y-5">
      <Alert className="border-emerald-200 bg-emerald-50/70 text-emerald-950">
        <ShieldCheck className="size-4" />
        <AlertDescription>
          This report is read-only. Athena does not delete or move any file.
        </AlertDescription>
      </Alert>

      <div className="flex w-fit gap-1 rounded-xl bg-slate-200/70 p-1">
        <button
          type="button"
          onClick={() => onKind('exact')}
          className={cn(
            'rounded-lg px-4 py-2 text-sm font-medium transition-colors',
            kind === 'exact' ? 'bg-white shadow-sm' : 'text-slate-600',
          )}
        >
          Exact copies
          <span className="ml-2 text-xs text-slate-400">{stats.exact_duplicate_groups}</span>
        </button>
        <button
          type="button"
          onClick={() => onKind('possible')}
          className={cn(
            'rounded-lg px-4 py-2 text-sm font-medium transition-colors',
            kind === 'possible' ? 'bg-white shadow-sm' : 'text-slate-600',
          )}
        >
          Possible matches
          <span className="ml-2 text-xs text-slate-400">
            {stats.possible_duplicate_groups}
          </span>
        </button>
      </div>

      {loading ? (
        <div className="space-y-4">
          {[1, 2].map((item) => (
            <Skeleton key={item} className="h-44 w-full rounded-xl" />
          ))}
        </div>
      ) : groups.length === 0 ? (
        <Card className="border-slate-200/80 bg-white shadow-none">
          <EmptyState
            icon={kind === 'exact' ? CheckCircle2 : Sparkles}
            title={kind === 'exact' ? 'No exact copies found' : 'No possible matches found'}
            description={
              kind === 'exact'
                ? 'Every indexed file has a unique fingerprint.'
                : 'No books currently share the same normalized title and author.'
            }
            action={{ label: 'Browse library', onClick: onOpenLibrary }}
          />
        </Card>
      ) : (
        <div className="space-y-4">
          {groups.map((group) => (
            <Card key={group.key} className="border-slate-200/80 bg-white shadow-none">
              <CardHeader className="border-b border-slate-100 pb-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <CardTitle>{group.books[0]?.title}</CardTitle>
                    <p className="mt-1 text-sm text-slate-500">
                      {group.books[0]?.author || 'Unknown author'}
                    </p>
                  </div>
                  <Badge
                    variant="secondary"
                    className={cn(
                      kind === 'exact'
                        ? 'bg-rose-50 text-rose-700'
                        : 'bg-amber-50 text-amber-700',
                    )}
                  >
                    {group.file_count} files
                  </Badge>
                </div>
              </CardHeader>
              <CardContent className="divide-y divide-slate-100 p-0">
                {group.books.map((book, index) => (
                  <div
                    key={book.id}
                    className="grid gap-2 px-5 py-4 sm:grid-cols-[1fr_auto] sm:items-center"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="truncate text-sm font-medium">{book.filename}</span>
                        {index === 0 && (
                          <Badge variant="outline" className="text-[10px]">
                            First found
                          </Badge>
                        )}
                      </div>
                      <p className="mt-1 truncate text-xs text-slate-500" title={book.path}>
                        {book.path}
                      </p>
                    </div>
                    <span className="text-xs text-slate-500">
                      {book.file_type.toUpperCase()} · {formatBytes(book.size_bytes)}
                    </span>
                  </div>
                ))}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}

function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon: typeof Search
  title: string
  description: string
  action?: { label: string; onClick: () => void }
}) {
  return (
    <div className="flex min-h-72 flex-col items-center justify-center px-6 py-12 text-center">
      <span className="mb-4 grid size-12 place-items-center rounded-2xl bg-slate-100 text-slate-600">
        <Icon className="size-5" />
      </span>
      <h3 className="text-base font-semibold">{title}</h3>
      <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">{description}</p>
      {action && (
        <Button variant="outline" className="mt-5" onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </div>
  )
}

function LoadingPage() {
  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[1, 2, 3, 4].map((item) => (
          <Skeleton key={item} className="h-36 rounded-xl" />
        ))}
      </div>
      <Skeleton className="h-80 rounded-xl" />
    </div>
  )
}

export default App
